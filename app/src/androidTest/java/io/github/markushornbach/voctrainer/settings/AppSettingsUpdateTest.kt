package io.github.markushornbach.voctrainer.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.WordType

/** The settings of an installation from its first start on: defaults, tips, first start, backup. */
@RunWith(AndroidJUnit4::class)
class AppSettingsUpdateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    @Before
    @After
    fun clear() {
        // AppSettings writes with apply(): a write still pending would recreate the deleted file, and the
        // next "fresh installation" would see old values. commit() waits until earlier writes are on disk.
        listOf(SETTINGS, DEVICE).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
    }

    private fun open() = AppSettings(context, SETTINGS, DEVICE)

    @Test
    fun freshInstallationStartsWithoutAutomaticSpeech() {
        assertFalse(open().autoSpeak.value)
    }

    @Test
    fun aStoredChoiceIsNeverOverwritten() {
        prefs.edit { putBoolean(AppSettings.KEY_AUTO_SPEAK, true) }
        assertTrue(open().autoSpeak.value)
        open().setAutoSpeak(false)
        assertFalse(open().autoSpeak.value)
    }

    @Test
    fun otherDailyGoalsStay() {
        prefs.edit { putInt(AppSettings.KEY_DAILY_GOAL, 4) }

        assertEquals(4, open().dailyGoal.value)
    }

    @Test
    fun aStaleIncludePhrasesValueIsIgnored() {
        // A stale key of a removed switch.
        prefs.edit {
            putBoolean("include_phrases", false)
            putBoolean(AppSettings.KEY_POINTS_INTRO_DONE, true)
        }
        val settings = open()
        val words = listOf(
            QuizWord("w", "bag", listOf("Tasche"), 1, null, 0),
            QuizWord("p", "Have you ever …?", listOf("Hast du jemals …?"), 1, null, 0, storedType = WordType.PHRASE, coreWord = "ever")
        )
        // The selections are in the book now; the default selection contains the phrase.
        val filter = QuizFilter()
        assertEquals(listOf("w", "p"), QuizSelection.filter(words, filter).map { it.id })
        assertFalse(settings.toBackup().toJson().containsKey("includePhrases"))
    }

    @Test
    fun aFreshInstallationHasAllTipsOpen() {
        assertEquals(TipState(), open().tips.value)
        // Also on the next start.
        assertEquals(TipState(), open().tips.value)
    }

    @Test
    fun tipsAreStoredAndResetTogether() {
        val settings = open()
        settings.tipUnderstood(Tip.AVATAR_CARD)
        settings.tipUnderstood(Tip.SELECTION)
        assertEquals(TipState(done = setOf(Tip.AVATAR_CARD, Tip.SELECTION)), open().tips.value)

        open().resetTips()
        assertEquals(TipState(), open().tips.value)
    }

    @Test
    fun theDoneTipsGoIntoTheBackupAndABackupWithoutThemLeavesThemAlone() {
        val settings = open()
        settings.tipUnderstood(Tip.TYPING)
        settings.tipUnderstood(Tip.ROUND_RESULT)
        val backup = settings.toBackup()
        assertEquals(listOf("round_result", "typing"), backup.tipsDone)

        settings.resetTips()
        settings.tipUnderstood(Tip.SELECTION)
        // No field: the tips stay as they are.
        settings.restoreBackup(backup.copy(tipsDone = null))
        assertEquals(setOf(Tip.SELECTION), settings.tips.value.done)
        // Unknown keys of a newer version are dropped.
        settings.restoreBackup(backup.copy(tipsDone = backup.tipsDone!! + "someday_tip"))
        assertEquals(setOf(Tip.TYPING, Tip.ROUND_RESULT), open().tips.value.done)
    }

    /** A backup with unknown tip keys restores without error. */
    @Test
    fun aBackupWithUnknownTipKeysRestores() {
        val settings = open()
        settings.restoreBackup(
            SettingsBackup(theme = AppTheme.SUNNY.key, tipsDone = listOf("points", "badges", "learned_word", "avatar_card", "selection"))
        )
        assertEquals(setOf(Tip.AVATAR_CARD, Tip.SELECTION), open().tips.value.done)
        assertEquals(AppTheme.SUNNY, open().theme.value)
    }

    @Test
    fun onlyAFreshInstallationGetsTheFirstStartAndItStaysUntilFinished() {
        val fresh = open()
        assertFalse(fresh.firstStartDone.value)
        // Closed halfway: the next start shows it again.
        assertFalse(open().firstStartDone.value)
        open().finishFirstStart()
        assertTrue(open().firstStartDone.value)
    }

    @Test
    fun theFirstStartIsNotInTheBackupAndARestoreSetsItDone() {
        val settings = open()
        // Every field of the backup, by name.
        val fields = settings.toBackup().toString()
        for (word in listOf("first", "seen", "Applied", "Hint")) assertFalse(word, fields.contains(word, ignoreCase = true))
        // A restore through the repository sets it (see BackupRestoreTest); the settings part alone keeps it.
        settings.restoreBackup(settings.toBackup())
        assertFalse(settings.firstStartDone.value)
        settings.finishFirstStart()
        assertTrue(open().firstStartDone.value)
    }

    @Test
    fun withoutTipsEveryTipIsDoneAndTheHintComesOnce() {
        val settings = open()
        settings.startWithoutTips()
        assertEquals(TipState(done = Tip.entries.toSet()), open().tips.value)
        assertTrue(open().tipsOffHintPending)
        open().consumeTipsOffHint()
        assertFalse(open().tipsOffHintPending)
    }

    @Test
    fun theFirstAppliedSelectionIsStored() {
        assertFalse(open().selectionApplied.value)
        open().markSelectionApplied()
        assertTrue(open().selectionApplied.value)
    }

    private companion object {
        const val SETTINGS = "settings_update_test"
        const val DEVICE = "device_update_test"
    }
}
