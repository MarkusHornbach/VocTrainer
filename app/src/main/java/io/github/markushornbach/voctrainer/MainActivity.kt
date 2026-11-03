package io.github.markushornbach.voctrainer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.markushornbach.voctrainer.reminder.Reminder
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.speech.Speaker
import io.github.markushornbach.voctrainer.ui.FixedUiScale
import io.github.markushornbach.voctrainer.ui.QuizViewModel
import io.github.markushornbach.voctrainer.ui.VocTrainerApp
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme

class MainActivity : ComponentActivity() {
    /** The same activity-scoped instance as `viewModel()` in [VocTrainerApp]. */
    private val quizViewModel: QuizViewModel by viewModels()

    private lateinit var settings: AppSettings

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) settings.setReminderEnabled(false)
            Reminder.schedule(this)
        }

    /** Ignores the system font size and display size (see [FixedUiScale]); must happen before the resources are used. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        FixedUiScale.overrideFor(newBase.resources.configuration)?.let(::applyOverrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = AppSettings.getInstance(this)
        // On every start, so the reminder survives updates and a changed time zone.
        Reminder.schedule(this)
        askForNotificationsOnce()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val theme by settings.theme.collectAsState()
            VocTrainerTheme(theme = theme) {
                VocTrainerApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        quizViewModel.refreshDay()
        // An English voice may have been installed while the app was in the background.
        Speaker.getInstance(this).recheck()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Speaker.release()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(Reminder.EXTRA_OPEN_START, false) == true) quizViewModel.requestStartScreen()
    }

    /** Android 13+: ask once on the first start with the reminder feature (it is on by default). */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || settings.notificationPermissionAsked) return
        settings.notificationPermissionAsked = true
        if (settings.reminderEnabled.value && !Reminder.notificationsAllowed(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
