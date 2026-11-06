package io.github.markushornbach.voctrainer.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.github.markushornbach.voctrainer.BuildConfig
import androidx.core.content.edit
import io.github.markushornbach.voctrainer.learning.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class AppTheme(val key: String, val label: String) {
    GAMING_DARK("gaming_dark", "Gaming dunkel"),
    OCEAN("ocean", "Ozean"),
    SUNNY("sunny", "Sonnig");

    companion object {
        val DEFAULT = OCEAN

        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class QuizMode(val key: String) {
    VOCABULARY("vocabulary"),
    VERBS("verbs");

    /**
     * Only vocabulary rounds feed the rewards: daily goal, streak, points, bonus and figure. Verb
     * training is a separate mode; it keeps its own levels and learning state only.
     */
    val countsForRewards: Boolean get() = this == VOCABULARY

    companion object {
        fun fromKey(key: String?): QuizMode = entries.firstOrNull { it.key == key } ?: VOCABULARY
    }
}

/**
 * "Verbformen" on the page "Üben": how many forms of an irregular verb are asked. [count] is the
 * value in the backup file.
 */
enum class VerbFormMode(val count: Int, val label: String) {
    TWO(2, "2 Formen (go – went)"),
    THREE(3, "3 Formen (go – went – gone)");

    companion object {
        val DEFAULT = TWO

        fun fromCount(count: Int?): VerbFormMode = entries.firstOrNull { it.count == count } ?: DEFAULT
    }
}

/**
 * Settings in SharedPreferences, shared by all books. The learner's choices go into the backup
 * file (see [SettingsBackup]) and come back with "Sicherung wiederherstellen". The quiz selections belong to the book (see [Book.quizFilter]).
 * [prefsName] and [devicePrefsName] differ only in tests.
 */
class AppSettings internal constructor(
    context: Context,
    prefsName: String = "settings",
    devicePrefsName: String = "device"
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** Excluded from Android backup (see `backup_rules.xml`), so a restored phone gets its own id. */
    private val devicePrefs = context.getSharedPreferences(devicePrefsName, Context.MODE_PRIVATE)

    /**
     * Stage of the figure in [slot] of [bookId] last shown on the start screen (0 if never), for
     * the wiggle after a new stage; device-local like the device id.
     */
    fun lastShownFigureStage(bookId: String, slot: Int): Int = devicePrefs.getInt(figureShownKey(bookId, slot), 0)

    fun setLastShownFigureStage(bookId: String, slot: Int, stage: Int) {
        devicePrefs.edit { putInt(figureShownKey(bookId, slot), stage) }
    }

    private fun figureShownKey(bookId: String, slot: Int) = "$KEY_FIGURE_SHOWN_STAGE${bookId}_$slot"

    private val _lastReleaseNotesVersion = MutableStateFlow(devicePrefs.getString(KEY_RELEASE_NOTES_VERSION, null))

    /** The version "Versionshinweise" last dealt with; device-local, in no backup. Null = none yet. */
    val lastReleaseNotesVersion: StateFlow<String?> = _lastReleaseNotesVersion.asStateFlow()

    fun setLastReleaseNotesVersion(version: String?) {
        devicePrefs.edit { if (version == null) remove(KEY_RELEASE_NOTES_VERSION) else putString(KEY_RELEASE_NOTES_VERSION, version) }
        _lastReleaseNotesVersion.value = version
    }

    private val _releaseNotesUpdateSimulated = MutableStateFlow(false)

    /** Test app only: the next start screen acts as if the running version had its own section in the notes. */
    val releaseNotesUpdateSimulated: StateFlow<Boolean> = _releaseNotesUpdateSimulated.asStateFlow()

    /** Test app only: a test build has no section of its own, so "Versionshinweise" would never come by itself. */
    fun simulateReleaseNotesUpdate() {
        if (!BuildConfig.DEBUG) return
        setLastReleaseNotesVersion(OLDER_VERSION_FOR_TEST)
        _releaseNotesUpdateSimulated.value = true
    }

    fun endReleaseNotesUpdateSimulation() {
        _releaseNotesUpdateSimulated.value = false
    }

    /** False keeps "Versionshinweise" from opening by itself; UI tests must not depend on the version of their build. */
    internal var releaseNotesAutoOpen = true

    /** Random id of this installation; tells apart the practice and points days of devices (e.g. after a restore on a new phone). */
    val deviceId: String = devicePrefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { id -> devicePrefs.edit { putString(KEY_DEVICE_ID, id) } }

    private val _dailyGoal = MutableStateFlow(prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL).coerceIn(DAILY_GOAL_RANGE))

    /** Finished rounds per day ("Tagesziel"). */
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    fun setDailyGoal(goal: Int) {
        val value = goal.coerceIn(DAILY_GOAL_RANGE)
        prefs.edit { putInt(KEY_DAILY_GOAL, value) }
        _dailyGoal.value = value
    }

    // Off by default in the test app (debug build), so a phone with both apps gets one reminder.
    private val _reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER_ENABLED, !BuildConfig.DEBUG))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMINDER_ENABLED, enabled) }
        _reminderEnabled.value = enabled
    }

    private val _reminderMinutes = MutableStateFlow(prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES))

    /** Time of the daily reminder in minutes after midnight. */
    val reminderMinutes: StateFlow<Int> = _reminderMinutes.asStateFlow()

    fun setReminderMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_REMINDER_MINUTES, minutes) }
        _reminderMinutes.value = minutes
    }

    /** The notification permission is requested once on the first start after the reminder feature. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_ASKED, value) }

    /** The first multiple-choice level cap ran on this device (see [finishLevelCapIntro]). */
    val levelCapIntroDone: Boolean get() = prefs.getBoolean(KEY_LEVEL_CAP_INTRO_DONE, false)

    private val _levelCapNotice = MutableStateFlow(prefs.getInt(KEY_LEVEL_CAP_NOTICE, NO_NOTICE).takeIf { it != NO_NOTICE })

    /** Number of words set to level 3 for the one-time notice; null = nothing to show. */
    val levelCapNotice: StateFlow<Int?> = _levelCapNotice.asStateFlow()

    /** [changed] words were capped by the first run; a notice is queued only if it is above 0. */
    fun finishLevelCapIntro(changed: Int) {
        prefs.edit {
            putBoolean(KEY_LEVEL_CAP_INTRO_DONE, true)
            putInt(KEY_LEVEL_CAP_COUNT, changed)
            putInt(KEY_LEVEL_CAP_NOTICE, if (changed > 0) changed else NO_NOTICE)
        }
        _levelCapNotice.value = changed.takeIf { it > 0 }
    }

    fun dismissLevelCapNotice() {
        prefs.edit { putInt(KEY_LEVEL_CAP_NOTICE, NO_NOTICE) }
        _levelCapNotice.value = null
    }

    private val _tips = MutableStateFlow(readTips())

    /** The done tips ("Tipp"), see [TipState]. Shared by all books, like the settings. */
    val tips: StateFlow<TipState> = _tips.asStateFlow()

    // Unknown keys (a newer version) are dropped.
    private fun readTips() = TipState(done = prefs.getStringSet(KEY_TIPS_DONE, null).orEmpty().mapNotNull(Tip::fromKey).toSet())

    private fun storeTips(state: TipState) {
        prefs.edit { putStringSet(KEY_TIPS_DONE, state.done.map { it.key }.toSet()) }
        _tips.value = state
    }

    /** "Verstanden" (or the secondary button): the tip is done. */
    fun tipUnderstood(tip: Tip) = storeTips(_tips.value.understood(tip))

    /** A round was started: "Los geht's" is no longer news, so its tip counts as done (see [TipState.roundStarted]). */
    fun roundStarted() {
        val next = _tips.value.roundStarted()
        if (next !== _tips.value) storeTips(next)
    }

    /** "Tipps wieder zeigen": all tips open again. */
    fun resetTips() = storeTips(TipState())

    // Stored only when the first start is finished, so a flow left halfway starts again.
    private val _firstStartDone = MutableStateFlow(prefs.getBoolean(KEY_FIRST_START_DONE, false))

    /** False while the first start (welcome, book choice, avatar choice) has to run; see [FirstStart]. */
    val firstStartDone: StateFlow<Boolean> = _firstStartDone.asStateFlow()

    private val _firstStartSimulated = MutableStateFlow(false)

    /** Test app only ("Erster Start simulieren"): the avatar choice is shown even if the book has an avatar. */
    val firstStartSimulated: StateFlow<Boolean> = _firstStartSimulated.asStateFlow()

    /** The learner reached the start screen at the end of the first start (also after a restored backup). */
    fun finishFirstStart() {
        prefs.edit { putBoolean(KEY_FIRST_START_DONE, true) }
        _firstStartDone.value = true
        _firstStartSimulated.value = false
    }

    /** Test app only ("Erster Start simulieren"): the first start again, without deleting any data. */
    fun simulateFirstStart() {
        if (!BuildConfig.DEBUG) return
        prefs.edit { putBoolean(KEY_FIRST_START_DONE, false) }
        _firstStartSimulated.value = true
        _firstStartDone.value = false
    }

    /** "Ohne Tipps starten": every tip done, and the hint how to switch them on comes once. */
    fun startWithoutTips() {
        storeTips(FirstStart.withoutTips())
        prefs.edit { putBoolean(KEY_TIPS_OFF_HINT, true) }
    }

    /** The hint after "Ohne Tipps starten" is still to come; [consumeTipsOffHint] once it is shown. */
    val tipsOffHintPending: Boolean get() = prefs.getBoolean(KEY_TIPS_OFF_HINT, false)

    fun consumeTipsOffHint() = prefs.edit { putBoolean(KEY_TIPS_OFF_HINT, false) }

    private val _selectionApplied = MutableStateFlow(prefs.getBoolean(KEY_SELECTION_APPLIED, false))

    /** "Auswahl übernehmen" was used once: the trigger of the tip [Tip.START_BUTTON]. */
    val selectionApplied: StateFlow<Boolean> = _selectionApplied.asStateFlow()

    fun markSelectionApplied() {
        if (_selectionApplied.value) return
        prefs.edit { putBoolean(KEY_SELECTION_APPLIED, true) }
        _selectionApplied.value = true
    }

    /** Test app only ("Tipp sofort zeigen"): the tip comes as soon as its target is visible; nothing is stored. */
    fun showTipNow(tip: Tip) {
        if (!BuildConfig.DEBUG) return
        _tips.value = _tips.value.forcedNow(tip)
    }

    private val _tipsOnePerVisit = MutableStateFlow(!BuildConfig.DEBUG || prefs.getBoolean(KEY_TIPS_ONE_PER_VISIT, true))

    /** At most one tip per visit of the start screen. Test app only: "Ein Tipp pro Besuch" can switch it off; always on in release builds. */
    val tipsOnePerVisit: StateFlow<Boolean> = _tipsOnePerVisit.asStateFlow()

    fun setTipsOnePerVisit(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit { putBoolean(KEY_TIPS_ONE_PER_VISIT, enabled) }
        _tipsOnePerVisit.value = enabled
    }

    private val _markCorrectAnswer = MutableStateFlow(BuildConfig.DEBUG && prefs.getBoolean(KEY_MARK_CORRECT, false))

    /** Test app only: "Richtige Antwort markieren" – a small marker on the correct option. Always off in release builds. */
    val markCorrectAnswer: StateFlow<Boolean> = _markCorrectAnswer.asStateFlow()

    fun setMarkCorrectAnswer(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit { putBoolean(KEY_MARK_CORRECT, enabled) }
        _markCorrectAnswer.value = enabled
    }

    init {
        // Test app only: the date shift survives a restart; release builds always use the real date.
        AppClock.setOffsetDays(if (BuildConfig.DEBUG) prefs.getInt(KEY_DATE_SHIFT_DAYS, 0) else 0)
    }

    /** Test app only ("Datum verschieben"): simulate today + [days]; 0 = real date. */
    fun setDateShiftDays(days: Int) {
        if (!BuildConfig.DEBUG) return
        prefs.edit { putInt(KEY_DATE_SHIFT_DAYS, days) }
        AppClock.setOffsetDays(days)
    }

    private val _theme = MutableStateFlow(AppTheme.fromKey(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    // Theme and vibration are stored only once changed, so new defaults also reach installations
    // where they were never changed.
    private val _vibration = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, false))

    /** "Vibration bei falschen Antworten" (default off). */
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit { putString(KEY_THEME, theme.key) }
        _theme.value = theme
    }

    fun setVibration(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_VIBRATION, enabled) }
        _vibration.value = enabled
    }

    private val _autoSpeak = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SPEAK, AUTO_SPEAK_DEFAULT))

    /** "Wörter automatisch vorsprechen" (default off); the speaker button works either way. */
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    fun setAutoSpeak(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SPEAK, enabled) }
        _autoSpeak.value = enabled
    }

    // Always on in the debug build, the separate test app, so levels can be set for testing.
    private val _adminMode = MutableStateFlow(BuildConfig.DEBUG || prefs.getBoolean(KEY_ADMIN_MODE, false))

    /**
     * Admin mode: without it, learning levels can only be lowered by hand, never raised.
     * Always on in debug builds (the test app "VocTrainer Debug"); in release builds off, and
     * there is no UI to switch it on.
     */
    val adminMode: StateFlow<Boolean> = _adminMode.asStateFlow()

    fun setAdminMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ADMIN_MODE, enabled) }
        _adminMode.value = enabled
    }

    private val _editButtonInQuiz = MutableStateFlow(prefs.getBoolean(KEY_EDIT_BUTTON_IN_QUIZ, EDIT_BUTTON_IN_QUIZ_DEFAULT))

    /**
     * "Bearbeiten-Knopf in der Abfrage": a pencil on vocabulary questions that opens the edit page
     * of the word (default off).
     */
    val editButtonInQuiz: StateFlow<Boolean> = _editButtonInQuiz.asStateFlow()

    fun setEditButtonInQuiz(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_EDIT_BUTTON_IN_QUIZ, enabled) }
        _editButtonInQuiz.value = enabled
    }

    private val _quizMode = MutableStateFlow(QuizMode.fromKey(prefs.getString(KEY_QUIZ_MODE, null)))
    val quizMode: StateFlow<QuizMode> = _quizMode.asStateFlow()

    fun setQuizMode(mode: QuizMode) {
        prefs.edit { putString(KEY_QUIZ_MODE, mode.key) }
        _quizMode.value = mode
    }

    private val _verbFormMode = MutableStateFlow(VerbFormMode.fromCount(prefs.getInt(KEY_VERB_FORMS, VerbFormMode.DEFAULT.count)))

    /** "Verbformen" (default 2 Formen); verb levels stay the same in both modes. */
    val verbFormMode: StateFlow<VerbFormMode> = _verbFormMode.asStateFlow()

    fun setVerbFormMode(mode: VerbFormMode) {
        prefs.edit { putInt(KEY_VERB_FORMS, mode.count) }
        _verbFormMode.value = mode
    }

    private val _restoreCount = MutableStateFlow(0)

    /** Counts restores of a backup, so screens can reload values they keep in memory (quiz selections). */
    val restoreCount: StateFlow<Int> = _restoreCount.asStateFlow()

    /** Called once at the end of every "Sicherung wiederherstellen", with or without settings in the file. */
    fun markRestored() {
        _restoreCount.value++
    }

    /** The learner's current settings for the backup file (effective values, defaults included). */
    fun toBackup(): SettingsBackup = SettingsBackup(
        theme = theme.value.key,
        vibration = vibration.value,
        autoSpeak = autoSpeak.value,
        editButtonInQuiz = editButtonInQuiz.value,
        quizMode = quizMode.value.key,
        verbForms = verbFormMode.value.count,
        dailyGoal = dailyGoal.value,
        reminderEnabled = reminderEnabled.value,
        reminderMinutes = reminderMinutes.value,
        levelCapIntroDone = levelCapIntroDone,
        levelCapNotice = levelCapNotice.value ?: 0,
        levelCapCount = prefs.getInt(KEY_LEVEL_CAP_COUNT, 0),
        tipsDone = tips.value.done.map { it.key }.sorted()
    )

    /**
     * "Sicherung wiederherstellen": takes over the valid values of [backup]; missing or invalid
     * ones keep the current value, and a backup without done tips leaves the tips as they are.
     * The caller reschedules the reminder.
     */
    fun restoreBackup(backup: SettingsBackup) {
        backup.tipsDone?.let { keys ->
            storeTips(_tips.value.copy(done = keys.mapNotNull(Tip::fromKey).toSet()))
        }
        prefs.edit {
            backup.validated().toPrefs().forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                }
            }
        }
        _theme.value = AppTheme.fromKey(prefs.getString(KEY_THEME, null))
        _vibration.value = prefs.getBoolean(KEY_VIBRATION, false)
        _autoSpeak.value = prefs.getBoolean(KEY_AUTO_SPEAK, AUTO_SPEAK_DEFAULT)
        _editButtonInQuiz.value = prefs.getBoolean(KEY_EDIT_BUTTON_IN_QUIZ, EDIT_BUTTON_IN_QUIZ_DEFAULT)
        _quizMode.value = QuizMode.fromKey(prefs.getString(KEY_QUIZ_MODE, null))
        _verbFormMode.value = VerbFormMode.fromCount(prefs.getInt(KEY_VERB_FORMS, VerbFormMode.DEFAULT.count))
        _dailyGoal.value = prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL).coerceIn(DAILY_GOAL_RANGE)
        _reminderEnabled.value = prefs.getBoolean(KEY_REMINDER_ENABLED, !BuildConfig.DEBUG)
        _reminderMinutes.value = prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
        _levelCapNotice.value = prefs.getInt(KEY_LEVEL_CAP_NOTICE, NO_NOTICE).takeIf { it != NO_NOTICE }
    }

    companion object {
        internal const val KEY_THEME = "theme"
        internal const val KEY_VIBRATION = "vibration"
        internal const val KEY_AUTO_SPEAK = "auto_speak"
        internal const val KEY_QUIZ_MODE = "quiz_mode"
        internal const val KEY_VERB_FORMS = "verb_forms"
        private const val KEY_ADMIN_MODE = "admin_mode"
        internal const val KEY_EDIT_BUTTON_IN_QUIZ = "edit_button_in_quiz"

        /** Automatic speech on a fresh installation. */
        const val AUTO_SPEAK_DEFAULT = false

        /** The edit button in the quiz is off until the learner switches it on. */
        const val EDIT_BUTTON_IN_QUIZ_DEFAULT = false

        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_FIGURE_SHOWN_STAGE = "figure_shown_stage_"
        private const val KEY_RELEASE_NOTES_VERSION = "release_notes_version"
        private const val OLDER_VERSION_FOR_TEST = "0.0.0"
        internal const val KEY_DAILY_GOAL = "daily_goal"
        internal const val KEY_REMINDER_ENABLED = "reminder_enabled"
        internal const val KEY_REMINDER_MINUTES = "reminder_minutes"
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        // One-time notices replaced by the tips: written only when a backup brings them, never read.
        internal const val KEY_POINTS_INTRO_DONE = "points_intro_done"
        internal const val KEY_POINTS_NOTICE = "points_notice"
        internal const val NO_NOTICE = -1
        internal const val POINTS_NOTICE_PENDING = 0
        internal const val KEY_SURE_INTRO_DONE = "sure_level_intro_done"
        internal const val KEY_SURE_NOTICE = "sure_level_notice"
        private const val KEY_MARK_CORRECT = "test_mark_correct_answer"
        private const val KEY_DATE_SHIFT_DAYS = "test_date_shift_days"
        internal const val KEY_LEVEL_CAP_INTRO_DONE = "level_cap_intro_done"
        internal const val KEY_LEVEL_CAP_NOTICE = "level_cap_notice"
        internal const val KEY_LEVEL_CAP_COUNT = "level_cap_count"
        private const val KEY_TIPS_DONE = "tips_done"
        // Device state, not in the backup file: a restore sets the first start done itself.
        private const val KEY_FIRST_START_DONE = "first_start_done"
        private const val KEY_TIPS_OFF_HINT = "tips_off_hint"
        private const val KEY_TIPS_ONE_PER_VISIT = "tips_one_per_visit"
        private const val KEY_SELECTION_APPLIED = "selection_applied"

        const val DEFAULT_DAILY_GOAL = 2
        /** At least 2 rounds; a smaller goal in a backup is raised to 2. */
        val DAILY_GOAL_RANGE = 2..5
        const val DEFAULT_REMINDER_MINUTES = 17 * 60

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }

        /** UI tests only: settings in own preference files, so a test never touches the app's settings. */
        @VisibleForTesting
        internal fun replaceInstanceForTest(settings: AppSettings?) {
            settings?.releaseNotesAutoOpen = false
            INSTANCE = settings
        }
    }
}
