package io.github.markushornbach.voctrainer.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.markushornbach.voctrainer.MainActivity
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.toQuizWord
import io.github.markushornbach.voctrainer.data.toRecord
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.Streak
import io.github.markushornbach.voctrainer.learning.SureToday
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.ui.PracticeTexts
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Daily reminder: one unique one-time work for the next reminder time, rescheduled after each run. */
object Reminder {
    private const val WORK_NAME = "daily_reminder"
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1
    const val EXTRA_OPEN_START = "open_start_screen"

    /** The next reminder at [minutes] after midnight: today if still ahead, otherwise tomorrow. */
    fun nextTime(now: ZonedDateTime, minutes: Int): ZonedDateTime {
        val today = now.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(now.zone)
        return if (today.isAfter(now.plusMinutes(1))) today else today.plusDays(1)
    }

    /**
     * Plans the next reminder (or cancels it when switched off). Called on app start and after
     * a settings change ([ExistingWorkPolicy.REPLACE]) and by the worker itself after each run
     * ([ExistingWorkPolicy.APPEND_OR_REPLACE], so the running work is not cancelled).
     */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val settings = AppSettings.getInstance(context)
        val workManager = WorkManager.getInstance(context)
        if (!settings.reminderEnabled.value) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, nextTime(now, settings.reminderMinutes.value))
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay).build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, title: String, text: String) {
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_START, true)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn in the meantime: no reminder.
        }
    }
}

/** Shows the reminder if today's goal is not reached yet, then plans the next one. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettings.getInstance(applicationContext)
        if (!settings.reminderEnabled.value) return Result.success()
        val repository = VocabularyRepository(VocTrainerDatabase.getInstance(applicationContext))
        val today = AppClock.today()
        val zone = ZoneId.systemDefault()
        val streak = Streak.compute(
            repository.allPracticeDays().map { it.toRecord() }, today, settings.deviceId, settings.dailyGoal.value
        )
        if (!streak.todayReached) {
            // Words of the saved vocabulary selection that can become sure today, as on the start screen.
            val words = repository.vocabularyInInsertionOrder.first().map { it.toQuizWord() }
            val filter = QuizSelection.normalize(words, repository.loadQuizFilter(QuizMode.VOCABULARY) ?: QuizFilter())
            val sureToday = SureToday.count(QuizSelection.filter(words, filter), today, zone)
            Reminder.show(
                applicationContext,
                PracticeTexts.reminderTitle(streak.current),
                PracticeTexts.reminderText(streak.current, sureToday)
            )
        }
        Reminder.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
