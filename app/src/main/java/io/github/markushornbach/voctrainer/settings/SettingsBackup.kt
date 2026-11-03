package io.github.markushornbach.voctrainer.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The learner's settings in the backup file (optional object "settings"). Only choices of the
 * learner, never device- or test-specific values (device id, shown figure stages, notification
 * permission, admin mode, test settings) and nothing of a book: the quiz selections are stored per
 * book in the backup's book list. Every field is optional: null = missing or invalid, and a
 * restore then keeps the current value. Unknown keys are ignored (e.g. "includePhrases",
 * "quizFilter", "verbQuizFilter"). The one-time notices (points, sure level) are read but never
 * written; they have no effect (the tips replaced them).
 */
data class SettingsBackup(
    val theme: String? = null,
    val vibration: Boolean? = null,
    val autoSpeak: Boolean? = null,
    val editButtonInQuiz: Boolean? = null,
    val quizMode: String? = null,
    /** "Verbformen": 2 or 3 ([VerbFormMode.count]); missing keeps the current value. */
    val verbForms: Int? = null,
    val dailyGoal: Int? = null,
    val reminderEnabled: Boolean? = null,
    val reminderMinutes: Int? = null,
    val pointsIntroDone: Boolean? = null,
    val pointsNotice: Boolean? = null,
    val sureLevelIntroDone: Boolean? = null,
    val sureLevelNotice: Boolean? = null,
    val levelCapIntroDone: Boolean? = null,
    /** Number of words for the level-3 notice; 0 = nothing to show. */
    val levelCapNotice: Int? = null,
    val levelCapCount: Int? = null,
    /** Keys of the done tips ([Tip.key]); null = missing, which leaves the tips unchanged. */
    val tipsDone: List<String>? = null
) {
    /** The same values with every invalid one set to null (skipped on restore). */
    fun validated(): SettingsBackup = copy(
        theme = theme?.takeIf { key -> AppTheme.entries.any { it.key == key } },
        quizMode = quizMode?.takeIf { key -> QuizMode.entries.any { it.key == key } },
        verbForms = verbForms?.takeIf { count -> VerbFormMode.entries.any { it.count == count } },
        // A goal of 1 is raised to the smallest allowed goal.
        dailyGoal = dailyGoal?.takeIf { it in 1..AppSettings.DAILY_GOAL_RANGE.last }
            ?.coerceAtLeast(AppSettings.DAILY_GOAL_RANGE.first),
        reminderMinutes = reminderMinutes?.takeIf { it in 0 until MINUTES_PER_DAY },
        levelCapNotice = levelCapNotice?.takeIf { it >= 0 },
        levelCapCount = levelCapCount?.takeIf { it >= 0 }
    )

    val isEmpty: Boolean get() = this == SettingsBackup()

    fun toJson(): JsonObject = buildJsonObject {
        theme?.let { put(THEME, it) }
        vibration?.let { put(VIBRATION, it) }
        autoSpeak?.let { put(AUTO_SPEAK, it) }
        editButtonInQuiz?.let { put(EDIT_BUTTON_IN_QUIZ, it) }
        quizMode?.let { put(QUIZ_MODE, it) }
        verbForms?.let { put(VERB_FORMS, it) }
        dailyGoal?.let { put(DAILY_GOAL, it) }
        reminderEnabled?.let { put(REMINDER_ENABLED, it) }
        reminderMinutes?.let { put(REMINDER_MINUTES, it) }
        pointsIntroDone?.let { put(POINTS_INTRO_DONE, it) }
        pointsNotice?.let { put(POINTS_NOTICE, it) }
        sureLevelIntroDone?.let { put(SURE_LEVEL_INTRO_DONE, it) }
        sureLevelNotice?.let { put(SURE_LEVEL_NOTICE, it) }
        levelCapIntroDone?.let { put(LEVEL_CAP_INTRO_DONE, it) }
        levelCapNotice?.let { put(LEVEL_CAP_NOTICE, it) }
        levelCapCount?.let { put(LEVEL_CAP_COUNT, it) }
        tipsDone?.let { keys -> putJsonArray(TIPS_DONE) { keys.forEach { add(JsonPrimitive(it)) } } }
    }

    /**
     * The values to write into the settings preferences, by preference key; missing values are
     * left out. Call on a [validated] backup. The done tips are restored by [AppSettings.restoreBackup].
     */
    fun toPrefs(): Map<String, Any> = buildMap {
        theme?.let { put(AppSettings.KEY_THEME, it) }
        vibration?.let { put(AppSettings.KEY_VIBRATION, it) }
        autoSpeak?.let { put(AppSettings.KEY_AUTO_SPEAK, it) }
        editButtonInQuiz?.let { put(AppSettings.KEY_EDIT_BUTTON_IN_QUIZ, it) }
        quizMode?.let { put(AppSettings.KEY_QUIZ_MODE, it) }
        verbForms?.let { put(AppSettings.KEY_VERB_FORMS, it) }
        dailyGoal?.let { put(AppSettings.KEY_DAILY_GOAL, it) }
        reminderEnabled?.let { put(AppSettings.KEY_REMINDER_ENABLED, it) }
        reminderMinutes?.let { put(AppSettings.KEY_REMINDER_MINUTES, it) }
        pointsIntroDone?.let { put(AppSettings.KEY_POINTS_INTRO_DONE, it) }
        pointsNotice?.let {
            put(AppSettings.KEY_POINTS_NOTICE, if (it) AppSettings.POINTS_NOTICE_PENDING else AppSettings.NO_NOTICE)
        }
        sureLevelIntroDone?.let { put(AppSettings.KEY_SURE_INTRO_DONE, it) }
        sureLevelNotice?.let { put(AppSettings.KEY_SURE_NOTICE, it) }
        levelCapIntroDone?.let { put(AppSettings.KEY_LEVEL_CAP_INTRO_DONE, it) }
        levelCapNotice?.let { put(AppSettings.KEY_LEVEL_CAP_NOTICE, if (it > 0) it else AppSettings.NO_NOTICE) }
        levelCapCount?.let { put(AppSettings.KEY_LEVEL_CAP_COUNT, it) }
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        const val THEME = "theme"
        const val VIBRATION = "vibration"
        const val AUTO_SPEAK = "autoSpeak"
        const val EDIT_BUTTON_IN_QUIZ = "editButtonInQuiz"
        const val QUIZ_MODE = "quizMode"
        const val VERB_FORMS = "verbForms"
        const val DAILY_GOAL = "dailyGoal"
        const val REMINDER_ENABLED = "reminderEnabled"
        const val REMINDER_MINUTES = "reminderMinutes"
        const val POINTS_INTRO_DONE = "pointsIntroDone"
        const val POINTS_NOTICE = "pointsNotice"
        const val SURE_LEVEL_INTRO_DONE = "sureLevelIntroDone"
        const val SURE_LEVEL_NOTICE = "sureLevelNotice"
        const val LEVEL_CAP_INTRO_DONE = "levelCapIntroDone"
        const val LEVEL_CAP_NOTICE = "levelCapNotice"
        const val LEVEL_CAP_COUNT = "levelCapCount"
        const val TIPS_DONE = "tipsDone"

        /**
         * Reads the "settings" object leniently: a value of the wrong type or out of range is
         * skipped on its own, never the whole file. Anything but an object gives null.
         */
        fun fromJson(element: JsonElement?): SettingsBackup? {
            val obj = element as? JsonObject ?: return null
            fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun boolean(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            fun int(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            // A list of strings; other elements are skipped.
            fun strings(key: String) = (obj[key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
            return SettingsBackup(
                theme = string(THEME),
                vibration = boolean(VIBRATION),
                autoSpeak = boolean(AUTO_SPEAK),
                editButtonInQuiz = boolean(EDIT_BUTTON_IN_QUIZ),
                quizMode = string(QUIZ_MODE),
                verbForms = int(VERB_FORMS),
                dailyGoal = int(DAILY_GOAL),
                reminderEnabled = boolean(REMINDER_ENABLED),
                reminderMinutes = int(REMINDER_MINUTES),
                pointsIntroDone = boolean(POINTS_INTRO_DONE),
                pointsNotice = boolean(POINTS_NOTICE),
                sureLevelIntroDone = boolean(SURE_LEVEL_INTRO_DONE),
                sureLevelNotice = boolean(SURE_LEVEL_NOTICE),
                levelCapIntroDone = boolean(LEVEL_CAP_INTRO_DONE),
                levelCapNotice = int(LEVEL_CAP_NOTICE),
                levelCapCount = int(LEVEL_CAP_COUNT),
                tipsDone = strings(TIPS_DONE)
            ).validated()
        }
    }
}
