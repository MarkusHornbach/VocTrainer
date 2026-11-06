package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.setTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The page "Versionshinweise": from the settings and once by itself after an update, never in the way of anything else. */
@RunWith(AndroidJUnit4::class)
class ReleaseNotesFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings
    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    private companion object {
        const val SETTINGS = "test_release_notes_settings"
        const val DEVICE = "test_release_notes_device"
        const val TIMEOUT = 10_000L
        const val WAIT = 1_500L
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        VocTrainerDatabase.replaceInstanceForTest(db)
        settings = AppSettings(context, SETTINGS, DEVICE).apply { setAutoSpeak(false) }
        AppSettings.replaceInstanceForTest(settings)
        settings.releaseNotesAutoOpen = true
        settings.setTipsDone(Tip.entries.toSet())
        runBlocking {
            db.vocabularyDao().insertAll(
                listOf("cat" to "Katze", "dog" to "Hund", "fish" to "Fisch", "bird" to "Vogel", "horse" to "Pferd", "mouse" to "Maus")
                    .map { (english, german) ->
                        Vocabulary(english = english, germanMeanings = listOf(german), unit = 1, part = "A", coreWord = english)
                    }
                    .inTestBook()
            )
        }
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

    private fun launch() {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        compose.waitForIdle()
    }

    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun pageOpen() = exists(ReleaseNotesTexts.TITLE) && !exists("Einstellungen")

    private fun waitForPage() = compose.waitUntil(TIMEOUT) { pageOpen() }

    private fun assertNoPage() {
        val until = System.currentTimeMillis() + WAIT
        while (System.currentTimeMillis() < until) {
            compose.waitForIdle()
            assertFalse("the page must not open", pageOpen())
            Thread.sleep(100)
        }
    }

    private fun back() = compose.onNodeWithContentDescription("Zurück").performClick()

    private fun startScreenOpen() = compose.onAllNodes(hasContentDescription("Einstellungen")).fetchSemanticsNodes().isNotEmpty()

    private fun waitForStartScreen() = compose.waitUntil(TIMEOUT) { startScreenOpen() }

    @Test
    fun settingsEntryOpensTheHistoryAndBackLeadsToTheSettings() {
        settings.finishFirstStart()
        settings.setLastReleaseNotesVersion(BuildConfig.VERSION_NAME)
        launch()
        waitForStartScreen()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText(ReleaseNotesTexts.TITLE).performScrollTo().performClick()
        waitForPage()
        compose.onNodeWithText("Version 1.0.0").assertExists()
        compose.onNodeWithText("03.11.2026").assertExists()
        back()
        compose.waitUntil(TIMEOUT) { exists("Einstellungen") }
    }

    @Test
    fun afterAnUpdateThePageOpensOnceAndBackLeadsToTheStartScreen() {
        settings.finishFirstStart()
        settings.simulateReleaseNotesUpdate()
        launch()
        waitForPage()
        assertEquals(BuildConfig.VERSION_NAME, settings.lastReleaseNotesVersion.value)
        back()
        waitForStartScreen()
        assertNoPage()
    }

    @Test
    fun theSameVersionOpensNothing() {
        settings.finishFirstStart()
        settings.setLastReleaseNotesVersion(BuildConfig.VERSION_NAME)
        launch()
        waitForStartScreen()
        assertNoPage()
    }

    @Test
    fun aFreshInstallationShowsNothingEvenAfterTheFirstStart() {
        launch()
        compose.waitUntil(TIMEOUT) { settings.lastReleaseNotesVersion.value == BuildConfig.VERSION_NAME }
        assertNoPage()
        compose.runOnUiThread { settings.finishFirstStart() }
        waitForStartScreen()
        assertNoPage()
    }

    @Test
    fun anUpdateDuringTheFirstStartWaitsForItsEnd() {
        settings.simulateReleaseNotesUpdate()
        launch()
        assertNoPage()
        compose.runOnUiThread { settings.finishFirstStart() }
        waitForPage()
    }

    @Test
    fun thePageWaitsForTheEndOfARound() {
        settings.finishFirstStart()
        settings.setLastReleaseNotesVersion(BuildConfig.VERSION_NAME)
        launch()
        waitForStartScreen()
        compose.waitUntil(TIMEOUT) { quiz.startState.value.loaded }
        compose.runOnUiThread { quiz.startRound() }
        compose.waitUntil(TIMEOUT) { quiz.round.value is QuizRoundState.Asking }
        compose.runOnUiThread { settings.simulateReleaseNotesUpdate() }
        assertNoPage()
        compose.runOnUiThread { quiz.cancelRound() }
        waitForPage()
    }

    @Test
    fun aTipWaitsUntilThePageIsClosed() {
        settings.finishFirstStart()
        settings.setTipsDone(Tip.entries.toSet() - Tip.ALL_KNOWN)
        settings.simulateReleaseNotesUpdate()
        launch()
        waitForPage()
        assertFalse("no tip over the page", compose.onAllNodes(hasTestTag(TipTags.CARD), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        back()
        waitForStartScreen()
        // The compulsory avatar choice of this new book comes first; no tip appears over it.
        compose.waitUntil(TIMEOUT) { exists(FigureKind.DRAGON.displayName) }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(TipTags.CARD), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
