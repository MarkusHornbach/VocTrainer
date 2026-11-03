package io.github.markushornbach.voctrainer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SettingsBackupTest {
    private val full = SettingsBackup(
        theme = "gaming_dark", vibration = true, autoSpeak = false, editButtonInQuiz = true,
        quizMode = "verbs", verbForms = 3,
        dailyGoal = 5, reminderEnabled = false, reminderMinutes = 7 * 60, pointsIntroDone = true, pointsNotice = true,
        sureLevelIntroDone = true, sureLevelNotice = false, levelCapIntroDone = true, levelCapNotice = 12,
        levelCapCount = 12, tipsDone = listOf("badges", "points")
    )

    private val includedJsonKeys = setOf(
        "theme", "vibration", "autoSpeak", "editButtonInQuiz", "quizMode", "verbForms", "dailyGoal", "reminderEnabled", "reminderMinutes", "pointsIntroDone", "pointsNotice",
        "sureLevelIntroDone", "sureLevelNotice", "levelCapIntroDone", "levelCapNotice", "levelCapCount", "tipsDone"
    )

    private val excludedKeys = listOf(
        "device_id", "deviceId", "figure_shown_stage_", "notification_permission_asked", "admin_mode", "adminMode",
        "test_mark_correct_answer", "test_date_shift_days",
        // The quiz selections belong to the book (in the backup's book list), not to the settings.
        "quizFilter", "verbQuizFilter", "quiz_filter", "verb_quiz_filter"
    )

    @Test
    fun everyIncludedKeyRoundTrips() {
        val json = full.toJson()

        assertEquals(includedJsonKeys, json.keys)
        assertEquals(full, SettingsBackup.fromJson(json))
    }

    @Test
    fun everyFieldIsSetInTheTestValue() {
        // Guards the round trip above: a new field must be added to the test.
        assertTrue(full.toString().split(", ").none { it.endsWith("=null") || it.endsWith("=null)") })
    }

    @Test
    fun excludedKeysNeverAppear() {
        val text = full.toJson().toString() + full.toPrefs().keys
        for (key in excludedKeys) assertFalse(key, text.contains(key))
    }

    @Test
    fun prefsUseTheStoredKeysAndValues() {
        val prefs = full.toPrefs()

        // The done tips are restored by AppSettings itself, not as a single preference.
        assertEquals(includedJsonKeys.size - 1, prefs.size)
        assertEquals("gaming_dark", prefs["theme"])
        assertEquals(5, prefs["daily_goal"])
        assertEquals(7 * 60, prefs["reminder_minutes"])
        assertEquals(AppSettings.POINTS_NOTICE_PENDING, prefs["points_notice"])
        assertEquals(12, prefs["level_cap_notice"])
    }

    @Test
    fun noNoticesAreStoredAsNoNotice() {
        val prefs = SettingsBackup(pointsNotice = false, levelCapNotice = 0).toPrefs()

        assertEquals(mapOf("points_notice" to AppSettings.NO_NOTICE, "level_cap_notice" to AppSettings.NO_NOTICE), prefs)
    }

    @Test
    fun missingValuesAreLeftOut() {
        assertTrue(SettingsBackup().toPrefs().isEmpty())
        assertTrue(SettingsBackup().toJson().isEmpty())
        assertTrue(SettingsBackup().isEmpty)
    }

    @Test
    fun invalidValuesAreDroppedOneByOne() {
        val checked = full.copy(
            theme = "pink", quizMode = "grammar", verbForms = 4,
            dailyGoal = 0, reminderMinutes = -1, levelCapNotice = -1, levelCapCount = -5
        ).validated()

        assertNull(checked.theme)
        assertNull(checked.quizMode)
        assertNull(checked.verbForms)
        assertNull(checked.dailyGoal)
        assertNull(checked.reminderMinutes)
        assertNull(checked.levelCapNotice)
        assertNull(checked.levelCapCount)
        assertEquals(true, checked.vibration)
        assertEquals(false, checked.autoSpeak)
    }

    @Test
    fun allThemesAndModesAndTheGoalRangeAreValid() {
        for (theme in AppTheme.entries) assertEquals(theme.key, SettingsBackup(theme = theme.key).validated().theme)
        for (mode in QuizMode.entries) assertEquals(mode.key, SettingsBackup(quizMode = mode.key).validated().quizMode)
        for (mode in VerbFormMode.entries) assertEquals(mode.count, SettingsBackup(verbForms = mode.count).validated().verbForms)
        for (goal in AppSettings.DAILY_GOAL_RANGE) assertEquals(goal, SettingsBackup(dailyGoal = goal).validated().dailyGoal)
        assertEquals(23 * 60 + 59, SettingsBackup(reminderMinutes = 23 * 60 + 59).validated().reminderMinutes)
    }

    @Test
    fun includePhrasesInABackupIsIgnored() {
        for (value in listOf(true, false)) {
            val old = buildJsonObject {
                full.toJson().forEach { (key, element) -> put(key, element) }
                put("includePhrases", value)
            }
            assertEquals(full, SettingsBackup.fromJson(old))
        }
        assertFalse(full.toJson().containsKey("includePhrases"))
        assertFalse(full.toPrefs().containsKey("include_phrases"))
    }

    /** Quiz selections in the settings block are ignored (they belong to the book). */
    @Test
    fun quizSelectionsInTheSettingsAreIgnored() {
        val old = buildJsonObject {
            full.toJson().forEach { (key, element) -> put(key, element) }
            put("quizFilter", """{"unit":"3","parts":["B"]}""")
            put("verbQuizFilter", """{"unit":"none"}""")
        }
        val restored = SettingsBackup.fromJson(old)!!
        assertEquals(full, restored)
        assertFalse(restored.toPrefs().keys.any { it.contains("filter") })
    }

    @Test
    fun aBackupWithoutTipsLeavesThemOut() {
        val old = buildJsonObject {
            full.toJson().filterKeys { it != "tipsDone" }.forEach { (key, element) -> put(key, element) }
        }
        assertNull(SettingsBackup.fromJson(old)!!.tipsDone)
        assertEquals(full.copy(tipsDone = null), SettingsBackup.fromJson(old))
    }

    @Test
    fun tipsDoneKeepsOnlyStrings() {
        val json = buildJsonObject {
            put("tipsDone", JsonArray(listOf(JsonPrimitive("points"), JsonPrimitive(3), JsonPrimitive("someday_tip"))))
        }
        // Unknown keys stay in the backup; AppSettings drops them when restoring.
        assertEquals(listOf("points", "someday_tip"), SettingsBackup.fromJson(json)!!.tipsDone)
        assertNull(SettingsBackup.fromJson(buildJsonObject { put("tipsDone", "points") })!!.tipsDone)
        assertEquals(emptyList<String>(), SettingsBackup.fromJson(SettingsBackup(tipsDone = emptyList()).toJson())!!.tipsDone)
    }

    /** A backup without "verbForms": the restore keeps the current value (default 2 Formen). */
    @Test
    fun aBackupWithoutVerbFormsLeavesThemOut() {
        val old = buildJsonObject {
            full.toJson().filterKeys { it != "verbForms" }.forEach { (key, element) -> put(key, element) }
        }
        val restored = SettingsBackup.fromJson(old)!!
        assertNull(restored.verbForms)
        assertFalse(restored.toPrefs().containsKey(AppSettings.KEY_VERB_FORMS))
        assertEquals(VerbFormMode.TWO, VerbFormMode.fromCount(restored.verbForms))
    }

    @Test
    fun invalidVerbFormsAreSkippedAlone() {
        for (bad in listOf(JsonPrimitive(1), JsonPrimitive(4), JsonPrimitive("3"), JsonPrimitive(true))) {
            val json = buildJsonObject {
                put("verbForms", bad)
                put("vibration", true)
            }
            val restored = SettingsBackup.fromJson(json)!!
            assertNull(bad.toString(), restored.verbForms)
            assertEquals(true, restored.vibration)
        }
        assertEquals(mapOf(AppSettings.KEY_VERB_FORMS to 3), SettingsBackup(verbForms = 3).toPrefs())
    }

    @Test
    fun aDailyGoalOfOneBecomesTwo() {
        assertEquals(2, SettingsBackup(dailyGoal = 1).validated().dailyGoal)
        assertEquals(2, SettingsBackup.fromJson(SettingsBackup(dailyGoal = 1, vibration = true).toJson())!!.dailyGoal)
        assertNull(SettingsBackup(dailyGoal = 6).validated().dailyGoal)
    }
}
