package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The page "Testwerkzeuge": collapsed groups, fixed date bar, level toggle with visible feedback, reset group. */
@RunWith(AndroidJUnit4::class)
class TestToolsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_tools_screen_settings"
        const val DEVICE = "test_tools_screen_device"
        const val TIMEOUT = 5_000L
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        settings = AppSettings(context, SETTINGS, DEVICE)
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            db.vocabularyDao().insertAll(
                listOf(Vocabulary(english = "cat", germanMeanings = listOf("Katze"), unit = 1, part = "A", coreWord = "cat")).inTestBook()
            )
        }
        compose.setContent { VocTrainerTheme { TestToolsScreen(onBack = {}, onShowTip = {}) } }
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        VocTrainerDatabase.replaceInstanceForTest(null)
        AppSettings.replaceInstanceForTest(null)
        db.closeAfterRunningQueries()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    private fun open(group: TestToolGroup) = compose.onNodeWithTag(group.tag).performScrollTo().performClick()

    @Test
    fun opensWithAllGroupsCollapsed() {
        TestToolGroup.entries.forEach { compose.onNodeWithTag(it.tag).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Lernlevel der Auswahl setzen").assertDoesNotExist()
        compose.onNodeWithText("Alle Vokabeln entfernen").assertDoesNotExist()
    }

    @Test
    fun groupExpandsAndCollapses() {
        open(TestToolGroup.LEVEL)
        compose.onNodeWithText("Lernlevel der Auswahl setzen").assertExists()
        open(TestToolGroup.LEVEL)
        compose.onNodeWithText("Lernlevel der Auswahl setzen").assertDoesNotExist()
    }

    @Test
    fun expandAllAndCollapseAll() {
        compose.onNodeWithTag("testGroupsExpandAll").performClick()
        compose.onNodeWithText("Lernlevel der Auswahl setzen").assertExists()
        compose.onNodeWithText("Nur Test-App", substring = true).assertExists()
        compose.onNodeWithTag("testGroupsCollapseAll").performClick()
        compose.onNodeWithText("Lernlevel der Auswahl setzen").assertDoesNotExist()
    }

    @Test
    fun dateBarStaysVisibleAfterScrollingAndPlusOneMovesTheDate() {
        compose.onNodeWithTag("testGroupsExpandAll").performClick()
        compose.onNodeWithTag(TestToolGroup.RESET.tag).performScrollTo()
        compose.onNodeWithTag("testDateBar").assertIsDisplayed()
        compose.onNodeWithText("+1").performClick()
        compose.waitUntil(TIMEOUT) { AppClock.offsetDays.value == 1 }
        compose.onNodeWithTag("testDateBar").assertIsDisplayed()
        compose.onNodeWithText("Echtes Datum").performClick()
        compose.waitUntil(TIMEOUT) { AppClock.offsetDays.value == 0 }
    }

    @Test
    fun levelToggleSetsTheLevelOfTheChosenModeAndShowsTheChipAsSelected() {
        open(TestToolGroup.LEVEL)
        compose.onNodeWithTag("levelMode_${QuizMode.VOCABULARY.name}").performScrollTo().performClick()
        compose.onNodeWithTag("level_2").performScrollTo().assertIsNotSelected().performClick()
        compose.waitUntil(TIMEOUT) {
            runBlocking { db.vocabularyDao().getAllIncludingDeleted().all { it.leitnerLevel == 2 } }
        }
        compose.waitUntil(TIMEOUT) { runCatching { compose.onNodeWithTag("level_2").assertIsSelected() }.isSuccess }
        // Pressing the selected level again still works.
        compose.onNodeWithTag("level_2").performClick()
        compose.onNodeWithTag("level_2").assertIsSelected()
        // Changing the toggle clears the highlight.
        compose.onNodeWithTag("levelMode_${QuizMode.VERBS.name}").performClick()
        compose.onNodeWithTag("level_2").assertIsNotSelected()
    }

    @Test
    fun resetGroupStillAsksBeforeRemovingTheVocabulary() {
        open(TestToolGroup.RESET)
        compose.onNodeWithText("Alle Vokabeln entfernen").performScrollTo().performClick()
        compose.onNodeWithText("Alle Vokabeln entfernen?").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(1, runBlocking { db.vocabularyDao().getAllIncludingDeleted().size })
        compose.onNodeWithText("Alle Vokabeln entfernen").performClick()
        compose.onNodeWithText("Entfernen").performClick()
        compose.waitUntil(TIMEOUT) { runBlocking { db.vocabularyDao().getAllIncludingDeleted().all { it.isDeleted } } }
    }

    @Test
    fun helpTextIsBehindTheQuestionMark() {
        open(TestToolGroup.BOOKS)
        compose.onNodeWithText("Legt das inaktive Buch", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("testHelp_testBook").performScrollTo().performClick()
        compose.onNodeWithText("Legt das inaktive Buch", substring = true).assertExists()
    }
}
