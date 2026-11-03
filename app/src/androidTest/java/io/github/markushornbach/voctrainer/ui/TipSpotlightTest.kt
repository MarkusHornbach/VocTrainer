package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inMemoryDatabase
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.TipState
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import io.github.markushornbach.voctrainer.settings.setTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The spotlight of the tips N4 and N5 on the start screen and the help page. */
@RunWith(AndroidJUnit4::class)
class TipSpotlightTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    /** Bumped to "restart" the app: new settings from the same files, new view models, new composition. */
    private var generation by mutableIntStateOf(0)

    private companion object {
        const val SETTINGS = "test_tip_settings"
        const val DEVICE = "test_tip_device"
        const val TIMEOUT = 10_000L

        /** Longer than the spotlight needs to settle, to be sure that no tip comes. */
        const val NO_TIP_WAIT = 2_000L
    }

    private val settings: AppSettings get() = AppSettings.getInstance(context)
    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    @Before
    fun setUp() {
        clearPrefs()
        db = inMemoryDatabase(context)
        repository = VocabularyRepository(db)
        VocTrainerDatabase.replaceInstanceForTest(db)
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        VocTrainerDatabase.replaceInstanceForTest(null)
        AppSettings.replaceInstanceForTest(null)
        db.closeAfterRunningQueries()
        clearPrefs()
    }

    private fun clearPrefs() {
        // commit(): a pending apply() of the last test must not bring old values back.
        listOf(SETTINGS, DEVICE).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
    }

    /**
     * A fresh installation unless the preferences were filled before; the first start is over and only
     * the tips in [open] are open (see FirstStartTest for the first ones).
     */
    private fun openSettings(open: Set<Tip> = setOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN)) {
        AppSettings.replaceInstanceForTest(
            AppSettings(context, SETTINGS, DEVICE).apply {
                setAutoSpeak(false)
                if (!firstStartDone.value) {
                    finishFirstStart()
                    setTipsDone(Tip.entries.toSet() - open)
                }
            }
        )
    }

    private fun launch() {
        compose.setContent { key(generation) { VocTrainerTheme { VocTrainerApp() } } }
    }

    private fun restart() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        openSettings()
        compose.runOnUiThread { generation++ }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTip() = compose.waitUntil(TIMEOUT) { exists(TipTags.CARD) }

    private fun assertNoTipFor(millis: Long = NO_TIP_WAIT) {
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            compose.waitForIdle()
            assertFalse("no tip expected", exists(TipTags.CARD))
            Thread.sleep(100)
        }
    }

    private fun chooseAvatar() {
        compose.waitUntil(TIMEOUT) { textExists(FigureTexts.choiceTitle(false)) }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }
    }

    /** The first finished round of a newcomer: the trigger of "Dein Avatar". */
    private fun firstRound() {
        runBlocking { repository.recordRoundCompleted(AppClock.today().toString(), settings.deviceId, settings.dailyGoal.value) }
        compose.waitUntil(TIMEOUT) { quiz.startState.value.firstRoundDone }
    }

    private fun bounds(description: String) =
        compose.onAllNodes(hasContentDescription(description)).onFirst().getUnclippedBoundsInRoot()

    @Test
    fun avatarTipShowsAtTheCardAfterTheFirstRoundAndStaysDoneAfterARestart() {
        openSettings()
        launch()
        chooseAvatar()
        assertNoTipFor()

        firstRound()
        waitForTip()
        compose.onNodeWithTag(TipTags.CARD).assertExistsWithText("Dein Avatar")
        compose.onNodeWithText("Punkte + gelernte Wörter → nächste Stufe", substring = true).assertExists()

        // The cut-out lies around the avatar card (with its stage bar).
        val hole = compose.onNodeWithTag(TipTags.HOLE).getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag(STAGE_BAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(hole.left <= bar.left && hole.right >= bar.right && hole.top <= bar.top && hole.bottom >= bar.bottom)

        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertTrue(Tip.AVATAR_CARD in settings.tips.value.done)
        // The closing tip waits for the next visit.
        assertNoTipFor()

        restart()
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) }
        assertTrue(Tip.AVATAR_CARD in settings.tips.value.done)
    }

    /** N5 comes on the next visit after N4 and points at "?". */
    @Test
    fun theClosingTipFollowsOnTheNextVisitAndHighlightsTheHelpIcon() {
        openSettings()
        launch()
        chooseAvatar()
        firstRound()
        waitForTip()
        compose.onNodeWithTag(TipTags.CARD).assertExistsWithText("Dein Avatar")
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertNoTipFor(1_000L)

        // The next visit: help page and back.
        compose.onNodeWithContentDescriptionFirst("Hilfe").performClick()
        compose.waitUntil(TIMEOUT) { textExists("BELOHNUNGEN") }
        compose.onAllNodes(hasContentDescription("Zurück")).onFirst().performClick()
        waitForTip()
        compose.onNodeWithTag(TipTags.CARD).assertExistsWithText("Jetzt kennst du alles Wichtige")
        val hole = compose.onNodeWithTag(TipTags.HOLE).getUnclippedBoundsInRoot()
        val help = bounds("Hilfe")
        assertTrue(hole.left <= help.left && hole.right >= help.right && hole.top <= help.top && hole.bottom >= help.bottom)
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertEquals(Tip.entries.toSet(), settings.tips.value.done)
    }

    @Test
    fun aTapOnTheDimmedScreenClosesWithoutDoneAndTheTipComesAgain() {
        openSettings()
        launch()
        chooseAvatar()
        firstRound()
        waitForTip()

        // Top left corner: the status bar area, never the card.
        compose.onNodeWithTag(TipTags.SCRIM).performTouchInput { click(Offset(8f, 8f)) }
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertFalse(Tip.AVATAR_CARD in settings.tips.value.done)
        assertNoTipFor(1_000L)

        // The next occasion: back on the start screen.
        compose.onNodeWithContentDescriptionFirst("Hilfe").performClick()
        compose.waitUntil(TIMEOUT) { textExists("BELOHNUNGEN") }
        compose.onAllNodes(hasContentDescription("Zurück")).onFirst().performClick()
        waitForTip()
        compose.onNodeWithTag(TipTags.CARD).assertExistsWithText("Dein Avatar")
    }

    @Test
    fun avatarTipHasOnlyVerstanden() {
        openSettings()
        launch()
        chooseAvatar()
        firstRound()
        waitForTip()
        compose.onNodeWithTag(TipTags.CARD).assertExistsWithText("Dein Avatar")

        assertFalse(textExists("Ansehen"))
        compose.onNodeWithText("Verstanden").performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertTrue(Tip.AVATAR_CARD in settings.tips.value.done)
        assertFalse(exists(TipTags.CARD))
    }

    @Test
    fun noTipOnTheSelectionScreenOrDuringAQuestion() {
        openSettings()
        launch()
        chooseAvatar()
        settings.markAllTipsDone()

        // The selection screen is open: a due tip waits.
        compose.runOnUiThread { quiz.openSelection() }
        settings.showTipNow(Tip.START_BUTTON)
        assertNoTipFor()
        compose.onNodeWithText(SelectionTexts.BACK).performScrollTo().performClick()
        waitForTip()
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }

        // A running round (with suggestions: the typing tip has no target).
        compose.runOnUiThread { quiz.startRound() }
        compose.waitUntil(TIMEOUT) { quiz.round.value is QuizRoundState.Asking }
        settings.showTipNow(Tip.START_BUTTON)
        assertNoTipFor()
    }

    @Test
    fun helpPageShowsGroupsTheStreakAndShowsTipsAgain() {
        openSettings()
        settings.markAllTipsDone()
        launch()
        chooseAvatar()

        compose.onNodeWithContentDescriptionFirst("Hilfe").performClick()
        compose.waitUntil(TIMEOUT) { textExists("ÜBEN") }
        for (text in listOf(
            "ÜBEN", InfoTexts.LEVELS_TITLE, InfoPage.VERBS.title, "BELOHNUNGEN", InfoTexts.POINTS_TITLE,
            InfoTexts.FIGURE_TITLE, PracticeTexts.STREAK_TITLE
        )) {
            assertTrue(text, textExists(text))
        }
        assertFalse(textExists("Tippe auf ein Thema, um die Erklärung zu öffnen."))

        compose.onNodeWithText(PracticeTexts.STREAK_TITLE).performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Tage in Folge mit Tagesziel")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("OK").performClick()

        compose.onNodeWithText("💡 " + TipTexts.SHOW_AGAIN).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { textExists(TipTexts.SHOWN_AGAIN) }
        assertEquals(TipState(), settings.tips.value)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsWithText(text: String) {
        assertExists()
        assertTrue(compose.onAllNodes(hasText(text) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag(TipTags.CARD)))
            .fetchSemanticsNodes().isNotEmpty())
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithContentDescriptionFirst(description: String) =
        onAllNodes(hasContentDescription(description)).onFirst()
}
