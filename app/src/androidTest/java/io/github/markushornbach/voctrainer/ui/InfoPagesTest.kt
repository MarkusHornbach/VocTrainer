package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.IrregularVerb
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.TipState
import io.github.markushornbach.voctrainer.settings.setTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The info pages (ⓘ): every ⓘ opens its page, the help page lists all pages in order, the streak
 * bar and the avatar card keep their behaviour, the line „Starte deine erste Runde!“, and every
 * page at font scale 1.3. Runs on an in-memory database with its own settings and no tips.
 */
@RunWith(AndroidJUnit4::class)
class InfoPagesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_info_pages_settings"
        const val DEVICE = "test_info_pages_device"
        const val TIMEOUT = 15_000L
        const val ROUND_TIMEOUT = 120_000L
        const val SAME = 1f
    }

    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    /** [count] new words with distinct meanings: more than one round, so new words are left after it. */
    private fun words(count: Int = 20) = (1..count).map { index ->
        Vocabulary(english = "word$index", germanMeanings = listOf("Wort$index"), unit = 1)
    }

    private val verbs = listOf("go" to "went", "see" to "saw", "take" to "took", "come" to "came", "give" to "gave")
        .map { (infinitive, past) -> IrregularVerb(infinitive = infinitive, simplePast = past, germanMeanings = listOf(infinitive), unit = 1) }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        VocTrainerDatabase.replaceInstanceForTest(db)
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

    /** The app on the start screen with no open tips, [words] and verbs in the book and an avatar at stage 0. */
    private fun launch(words: List<Vocabulary> = words()) {
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            finishFirstStart()
            setTipsDone(Tip.entries.toSet())
        }
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            db.vocabularyDao().insertAll(words.inTestBook())
            db.irregularVerbDao().insertAll(verbs.inTestBook())
            val now = System.currentTimeMillis()
            db.figureDao().upsert(
                Figure(slot = 0, kind = FigureKind.entries.first().key, pointsAtStart = 0, stage = 0, startedAt = now, updatedAt = now, bookId = TEST_BOOK_ID)
            )
        }
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        compose.waitUntil(TIMEOUT) {
            quiz.figureState.value.current != null && quiz.startState.value.loaded && quiz.streak.value != null
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun dialogOpen() = compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInWindow

    /** The open info page has [title] and [text]; "OK" closes it. */
    private fun assertPage(title: String, text: String) {
        compose.waitUntil(TIMEOUT) { dialogOpen() }
        assertTrue("title $title", compose.onAllNodes(hasText(title) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(isDialog())).onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(TIMEOUT) { !dialogOpen() }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performClick()
    }

    private fun finishRound() {
        compose.runOnUiThread { quiz.startRound() }
        compose.waitUntil(TIMEOUT) { quiz.round.value is QuizRoundState.Asking }
        val end = System.currentTimeMillis() + ROUND_TIMEOUT
        while (quiz.round.value !is QuizRoundState.Finished) {
            val state = quiz.round.value as? QuizRoundState.Asking
            if (state != null && state.phase == AnswerPhase.ANSWERING && state.typing == null) {
                compose.runOnUiThread { quiz.answer(state.correctIndex) }
            }
            assertTrue("round did not finish", System.currentTimeMillis() < end)
            Thread.sleep(150)
        }
        compose.waitUntil(TIMEOUT) { (quiz.round.value as? QuizRoundState.Finished)?.details != null }
    }

    private fun openSettingsPage(title: String) {
        compose.onAllNodes(hasContentDescription("Einstellungen")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { textExists(title) }
        compose.onNodeWithText(title).performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.onAllNodes(hasContentDescription("Zurück")).onFirst().performClick()
        compose.waitForIdle()
    }

    // ---- the ⓘ of the start screen ----

    @Test
    fun theStreakBarAndItsInfoOpenTheSamePageAndTheBarKeepsItsHeight() {
        launch()
        // Tapping the bar outside the ⓘ opens the streak page, too.
        compose.onNodeWithTag(STREAK_BAR_TAG).performTouchInput { click(centerLeft.copy(x = centerLeft.x + 40f)) }
        assertPage(PracticeTexts.STREAK_TITLE, "Tage in Folge mit Tagesziel")
        // The ⓘ lies inside the bar: the bar is as high as before (title + "Heute" line + padding).
        val bar = bounds(STREAK_BAR_TAG)
        val info = bounds(STREAK_INFO_TAG)
        assertTrue(info.top >= bar.top - SAME && info.bottom <= bar.bottom + SAME && info.right <= bar.right + SAME)
        if (context.resources.configuration.fontScale == 1f) {
            val expected = with(Density(context)) { 64.dp.toPx() }
            assertTrue("bar height ${bar.height} vs $expected", abs(bar.height - expected) <= SAME)
        }
        click(STREAK_INFO_TAG)
        assertPage(PracticeTexts.STREAK_TITLE, "Tage in Folge mit Tagesziel")
    }

    @Test
    fun theAvatarCardHasNoInfoAndOpensMeinAvatar() {
        launch()
        assertFalse(exists("avatar_info"))
        compose.onNodeWithTag(STAGE_BAR_TAG, useUnmergedTree = true).performClick()
        compose.waitUntil(TIMEOUT) { textExists(BadgeTexts.TITLE) }
        assertFalse(dialogOpen())
    }

    @Test
    fun meinAvatarHasTheInfoOfStagesPointsAndBadges() {
        launch()
        compose.onNodeWithTag(STAGE_BAR_TAG, useUnmergedTree = true).performClick()
        compose.waitUntil(TIMEOUT) { textExists(BadgeTexts.TITLE) }
        val infos = compose.onAllNodes(hasContentDescription(INFO_BUTTON_DESCRIPTION))
        infos[0].performClick()
        assertPage(InfoTexts.FIGURE_TITLE, "Jede Stufe braucht Punkte")
        assertFalse(textExists("Erreichte Stufen bleiben"))
        infos[1].performClick()
        assertPage(InfoTexts.POINTS_TITLE, "Verben: keine Punkte")
        click(BADGES_INFO_TAG)
        assertPage(InfoPage.BADGES.title, "Platin")
        assertFalse(textExists("Abzeichen bringen keine Punkte und bleiben für immer."))
    }

    @Test
    fun theProgressCardOpensTheMergedLevelsPage() {
        launch()
        compose.onNodeWithTag(PROGRESS_CARD_TAG).performClick()
        compose.waitUntil(TIMEOUT) { exists(LEVELS_INFO_TAG) }
        // The ⓘ sits in the header next to the title, and tapping it does not fold the card.
        assertTrue(bounds(LEVELS_INFO_TAG).top < bounds(PROGRESS_CARD_TAG).top + with(Density(context)) { 80.dp.toPx() })
        click(LEVELS_INFO_TAG)
        compose.waitUntil(TIMEOUT) { dialogOpen() }
        // The staircase, then the rules; no typing block and no link to a second page.
        for (step in InfoTexts.askSteps()) assertTrue(step.levels, textExists(step.levels))
        assertTrue(textExists(InfoTexts.RULES_TITLE))
        assertTrue(textExists("✔ Antwort richtig: +1 Level"))
        assertFalse(textExists("Beim Tippen"))
        assertFalse(textExists("So wirst du gefragt ›"))
        assertFalse(textExists("So wirst du gefragt"))
    }

    @Test
    fun theVerbModeOpensTheVerbPage() {
        launch()
        compose.runOnUiThread { quiz.selectMode(QuizMode.VERBS) }
        compose.waitUntil(TIMEOUT) { quiz.startState.value.mode == QuizMode.VERBS && quiz.startState.value.loaded }
        compose.onNodeWithTag(PROGRESS_CARD_TAG).performClick()
        compose.waitUntil(TIMEOUT) { exists(LEVELS_INFO_TAG) }
        click(LEVELS_INFO_TAG)
        compose.waitUntil(TIMEOUT) { dialogOpen() }
        assertTrue(textExists(InfoPage.VERBS.title))
        assertPage(InfoTexts.LEVELS_TITLE, "Unregelmäßige Verben: Simple Past")
        assertTrue(textExists(LevelQuestionKind.VERB_SIMPLE_PAST_CHOOSE.label))
        assertFalse(textExists(InfoTexts.RULES_TITLE))
    }

    @Test
    fun theSelectionScreenOpensWelcheWoerter() {
        launch()
        compose.runOnUiThread { quiz.openSelection() }
        compose.waitUntil(TIMEOUT) { exists(SelectionTexts.INFO_TAG) }
        click(SelectionTexts.INFO_TAG)
        assertPage(InfoPage.SELECTION.title, "Kapitel im Buch")
    }

    @Test
    fun theFirstRoundLineGoesAfterTheFirstRoundAndTheCardKeepsItsHeight() {
        launch()
        compose.waitUntil(TIMEOUT) { exists(FIRST_ROUND_TAG) }
        assertTrue(textExists(StartTexts.FIRST_ROUND))
        val before = bounds(PROGRESS_CARD_TAG).height

        finishRound()
        // The result screen: "Details" has its ⓘ.
        click(DETAILS_INFO_TAG)
        assertPage(InfoPage.DETAILS.title, RoundDetailTexts.SYMBOL_UP)

        compose.runOnUiThread { quiz.backToStart() }
        compose.waitUntil(TIMEOUT) { exists(PROGRESS_CARD_TAG) }
        compose.waitUntil(TIMEOUT) { !exists(FIRST_ROUND_TAG) }
        assertTrue(quiz.startState.value.nextRoundLine != null)
        assertEquals(before, bounds(PROGRESS_CARD_TAG).height, SAME)
    }

    // ---- the settings pages ----

    @Test
    fun theSettingsPagesOpenTheirInfoPages() {
        launch()
        openSettingsPage("Vokabeln verwalten")
        click(BOOKS_INFO_TAG)
        assertPage(InfoPage.BOOKS.title, "Lernstand jedes Buchs bleibt erhalten")
        compose.onNodeWithTag(BACKUP_INFO_TAG).performScrollTo()
        click(BACKUP_INFO_TAG)
        assertPage(InfoPage.BOOKS.title, "Lernstand jedes Buchs bleibt erhalten")
        // "Lernstand zurücksetzen" is the last section of the same page.
        compose.onNodeWithTag(RESTART_INFO_TAG).performScrollTo()
        click(RESTART_INFO_TAG)
        assertPage(InfoPage.RESTART.title, InfoTexts.RESTART_RESET_TITLE)
        back()

        compose.onNodeWithText("Tagesziel & Erinnerung").performClick()
        compose.waitUntil(TIMEOUT) { exists(PRACTICE_INFO_TAG) }
        click(PRACTICE_INFO_TAG)
        assertPage(InfoPage.PRACTICE_SETTINGS.title, "Nur wenn das Tagesziel noch fehlt")
    }

    // ---- the help page ----

    @Test
    fun theHelpPageListsAllGroupsAndPagesInOrderAndEachOpens() {
        launch()
        compose.onAllNodes(hasContentDescription("Hilfe")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { textExists(InfoGroup.SETTINGS.label) }
        val order = InfoGroup.entries.flatMap { group -> listOf(group.label) + InfoPage.entries.filter { it.group == group }.map { it.title } } +
            ("💡 " + TipTexts.SHOW_AGAIN)
        // The whole page is laid out (one scroll position): each entry lies below the one before it
        // (unclipped positions: the entries below the screen have empty clipped bounds).
        val tops = order.map { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals(order.toString(), tops.sorted(), tops)
        assertEquals(order.size, tops.distinct().size)

        assertFalse(textExists("So wirst du gefragt"))
        val distinct = mapOf(
            InfoPage.SELECTION to "Kapitel im Buch",
            InfoPage.LEVELS to InfoTexts.RULES_TITLE,
            InfoPage.VERBS to "Unregelmäßige Verben: Simple Past",
            InfoPage.POINTS to "Nur „Buch neu beginnen“ setzt Punkte auf 0",
            InfoPage.FIGURE to "Jede Stufe braucht Punkte",
            InfoPage.BADGES to "Platin",
            InfoPage.STREAK to "Tage in Folge mit Tagesziel",
            InfoPage.DETAILS to "Punkte für dieses Wort",
            InfoPage.BOOKS to "Lernstand jedes Buchs bleibt erhalten",
            InfoPage.RESTART to "Vorher wird eine Sicherung gespeichert",
            InfoPage.PRACTICE_SETTINGS to "Nur wenn das Tagesziel noch fehlt"
        )
        for (page in InfoPage.entries) {
            compose.onNodeWithText(page.title).performScrollTo().performClick()
            val title = if (page == InfoPage.VERBS) InfoTexts.LEVELS_TITLE else page.title
            assertPage(title, distinct.getValue(page))
        }
    }

    // ---- the staircase on a narrow page ----

    @Test
    fun theStaircaseBecomesRowsOnANarrowPage() {
        // The font size is fixed (FixedUiScale); only a narrow page (a small phone) stacks the steps. Both forms
        // show the three steps with their levels.
        var width by mutableStateOf(320.dp)
        compose.setContent {
            VocTrainerTheme { Box(modifier = Modifier.width(width)) { AskStaircase(InfoTexts.askSteps()) } }
        }
        for (narrow in listOf(false, true)) {
            compose.runOnIdle { width = if (narrow) 200.dp else 320.dp }
            compose.waitForIdle()
            val steps = InfoTexts.askSteps()
            val tops = steps.map { step -> compose.onNodeWithText(step.levels).fetchSemanticsNode().boundsInWindow }
            if (narrow) {
                // Stacked: highest step on top, each further right.
                assertTrue(tops[2].top < tops[1].top && tops[1].top < tops[0].top)
                assertTrue(tops[2].left > tops[1].left && tops[1].left > tops[0].left)
            } else {
                // Side by side: lowest on the left.
                assertTrue(tops[0].left < tops[1].left && tops[1].left < tops[2].left)
            }
        }
    }
}
