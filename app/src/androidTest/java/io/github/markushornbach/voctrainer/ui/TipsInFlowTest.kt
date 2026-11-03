package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.IrregularVerb
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.learning.AppClock
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The tips of the flow (Tippen, N3, N4, N5): one tip per visit of the start screen, the typing tip
 * (the keyboard must not move, the card stays fully visible), the result screen and the closing tip. Runs on an
 * in-memory database with its own settings.
 */
@RunWith(AndroidJUnit4::class)
class TipsInFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository
    private lateinit var settings: AppSettings

    /** Bumped to recreate the composition (a rotation): the same view models and settings. */
    private var generation by mutableIntStateOf(0)

    private companion object {
        const val SETTINGS = "test_tips_flow_settings"
        const val DEVICE = "test_tips_flow_device"
        const val TIMEOUT = 15_000L
        const val ROUND_TIMEOUT = 120_000L
        const val NO_TIP_WAIT = 2_000L
        const val SAME = 1f
    }

    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    // Level 0: English → German with suggestions (a plain round to finish); level 2: German → English with
    // suggestions; level 3 with a core word: typed.
    private fun words(level: Int, typed: Boolean = false) = listOf("bread", "cheese", "river", "chair", "garden", "window", "kitchen", "teacher")
        .mapIndexed { index, english ->
            Vocabulary(
                english = english, germanMeanings = listOf("Wort${'A' + index}"), unit = 1,
                leitnerLevel = level, highestLevel = level, coreWord = english.takeIf { typed || level >= 3 }
            )
        }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        repository = VocabularyRepository(db)
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

    /** The app with [open] as the only open tips, [words] in the book and an avatar at [stage]. */
    private fun launch(
        open: Set<Tip>,
        words: List<Vocabulary> = words(0),
        stage: Int = 0,
        verbs: List<IrregularVerb> = emptyList(),
        mode: QuizMode = QuizMode.VOCABULARY
    ) {
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            setQuizMode(mode)
            finishFirstStart()
            setTipsDone(Tip.entries.toSet() - open)
        }
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            db.vocabularyDao().insertAll(words.inTestBook())
            if (verbs.isNotEmpty()) db.irregularVerbDao().insertAll(verbs.inTestBook())
            val now = System.currentTimeMillis()
            db.figureDao().upsert(
                Figure(slot = 0, kind = FigureKind.entries.first().key, pointsAtStart = 0, stage = stage, startedAt = now, updatedAt = now, bookId = TEST_BOOK_ID)
            )
        }
        compose.runOnUiThread {
            // As MainActivity: edge to edge, the app handles the keyboard insets itself.
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent { key(generation) { VocTrainerTheme { VocTrainerApp() } } }
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null && quiz.startState.value.loaded }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTip(title: String) {
        compose.waitUntil(TIMEOUT) { exists(TipTags.CARD) }
        assertTrue("card of $title", compose.onAllNodes(hasText(title) and hasAnyAncestor(hasTestTag(TipTags.CARD))).fetchSemanticsNodes().isNotEmpty())
    }

    private fun understood() {
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
    }

    private fun assertNoTipFor(millis: Long = NO_TIP_WAIT) {
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            compose.waitForIdle()
            assertFalse("no tip expected", exists(TipTags.CARD))
            Thread.sleep(100)
        }
    }

    /** Leaves the start screen (help) and comes back: a new visit. */
    private fun leaveAndReturn() {
        compose.onAllNodes(hasContentDescription("Hilfe")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { textExists("BELOHNUNGEN") }
        compose.onAllNodes(hasContentDescription("Zurück")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) }
    }

    private fun startRound() {
        compose.runOnUiThread { quiz.startRound() }
        compose.waitUntil(TIMEOUT) { quiz.round.value is QuizRoundState.Asking }
    }

    private val asking: QuizRoundState.Asking? get() = quiz.round.value as? QuizRoundState.Asking

    private fun answerCorrectUntilFinished() {
        val end = System.currentTimeMillis() + ROUND_TIMEOUT
        while (quiz.round.value !is QuizRoundState.Finished) {
            val state = asking
            if (state != null && state.phase == AnswerPhase.ANSWERING && state.typing == null) {
                compose.runOnUiThread { quiz.answer(state.correctIndex) }
            }
            assertTrue("round did not finish", System.currentTimeMillis() < end)
            Thread.sleep(150)
        }
    }

    private fun imeBottom(): Int {
        var bottom = 0
        compose.runOnUiThread {
            bottom = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        return bottom
    }

    private fun bounds(tag: String): Rect =
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().first().boundsInWindow

    private fun assertSame(label: String, before: Rect, after: Rect) {
        for ((name, a, b) in listOf(
            Triple("left", before.left, after.left), Triple("top", before.top, after.top),
            Triple("right", before.right, after.right), Triple("bottom", before.bottom, after.bottom)
        )) assertTrue("$label $name: $a vs $b", abs(a - b) <= SAME)
    }

    /** The first finished round of the book: the trigger of "Dein Avatar". */
    private fun firstRound() {
        runBlocking { repository.recordRoundCompleted(AppClock.today().toString(), settings.deviceId, settings.dailyGoal.value) }
    }

    private fun Rect.containsRect(other: Rect) =
        left <= other.left + SAME && right >= other.right - SAME && top <= other.top + SAME && bottom >= other.bottom - SAME

    private fun textBounds(text: String): Rect =
        compose.onAllNodes(hasText(text, substring = true) and !hasAnyAncestor(hasTestTag(TipTags.CARD)))
            .fetchSemanticsNodes().first().boundsInWindow

    // ---- one tip per visit ----

    @Test
    fun onceTheLevelsTipIsDoneTheAvatarTipComesAndTheClosingTipOnTheNextVisit() {
        launch(open = setOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN))

        waitForTip("Dein Avatar")
        understood()
        // The closing tip waits for the next visit.
        assertNoTipFor()

        leaveAndReturn()
        waitForTip("Jetzt kennst du alles Wichtige")
        understood()
        assertEquals(Tip.entries.toSet(), settings.tips.value.done)
    }

    @Test
    fun theClosingTipFollowsTheAvatarTip() {
        launch(open = setOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN))
        waitForTip("Dein Avatar")
        compose.onNodeWithText("Verstanden").performClick()
        assertTrue(Tip.AVATAR_CARD in settings.tips.value.done)
        // A new start screen visit: open "Mein Avatar" by tapping the avatar and come back.
        compose.onNodeWithTag(STAGE_BAR_TAG, useUnmergedTree = true).performClick()
        compose.waitUntil(TIMEOUT) { textExists("Mein Avatar") }
        compose.onAllNodes(hasContentDescription("Zurück")).onFirst().performClick()
        waitForTip("Jetzt kennst du alles Wichtige")
    }

    @Test
    fun theSelectionScreenStartsANewVisit() {
        launch(open = setOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN))
        waitForTip("Dein Avatar")
        understood()
        assertNoTipFor()

        compose.runOnUiThread { quiz.openSelection() }
        compose.waitForIdle()
        compose.runOnUiThread { quiz.applySelection() }
        waitForTip("Jetzt kennst du alles Wichtige")
    }

    @Test
    fun theSwitchOfTheTestToolsTurnsTheLimitOff() {
        launch(open = setOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN))
        settings.setTipsOnePerVisit(false)

        waitForTip("Dein Avatar")
        understood()
        // Without the limit the next tip follows in the same visit.
        waitForTip("Jetzt kennst du alles Wichtige")
    }

    @Test
    fun aRecreationDuringAVisitShowsNoSecondTip() {
        launch(open = setOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN))
        waitForTip("Dein Avatar")

        // A new composition with the same view models, as after a rotation: the same tip, never the second one.
        compose.runOnUiThread { generation++ }
        compose.waitForIdle()
        waitForTip("Dein Avatar")
        understood()
        assertNoTipFor()
    }

    // ---- the typing tip ----

    @Test
    fun noTypingTipOnAQuestionWithSuggestions() {
        launch(open = setOf(Tip.TYPING), words = words(0))
        startRound()
        assertNoTipFor()
    }

    /**
     * Only "Mit Vorschlägen" is highlighted, the card is fully visible above the keyboard, and the
     * keyboard and the layout stay as they are.
     */
    @Test
    fun typingTipPointsAtMitVorschlaegenAndLeavesTheKeyboardAlone() {
        launch(open = setOf(Tip.TYPING), words = words(3))
        startRound()
        compose.waitUntil(TIMEOUT) { asking?.typing != null && exists(QuizTags.INPUT) && imeBottom() > 0 }
        waitForTip("Selbst tippen")

        val hole = bounds(TipTags.HOLE)
        assertTrue("hole around Mit Vorschlägen", hole.containsRect(bounds(QuizTags.CHOICE)))
        assertFalse("the input is not highlighted", hole.containsRect(bounds(QuizTags.INPUT)))
        assertFalse("Prüfen is not highlighted", hole.containsRect(bounds(QuizTags.CHECK)))

        // The card lies fully on the screen: below the status bar, above the keyboard.
        val card = bounds(TipTags.CARD)
        var windowHeight = 0
        var statusBar = 0
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            windowHeight = view.height
            statusBar = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        assertTrue("card top ${card.top} below the status bar $statusBar", card.top >= statusBar)
        assertTrue("card bottom ${card.bottom} above the keyboard", card.bottom <= windowHeight - imeBottom())
        // ... and does not cover the highlighted button.
        assertFalse(card.overlaps(hole))

        val imeOpen = imeBottom()
        val bottomOpen = bounds(QuizTags.BOTTOM)
        val inputOpen = bounds(QuizTags.INPUT)
        val promptOpen = bounds(QuizTags.PROMPT)
        compose.onNodeWithTag(QuizTags.INPUT, useUnmergedTree = true).assertIsFocused()

        understood()
        compose.waitForIdle()
        assertEquals("keyboard", imeOpen, imeBottom())
        assertSame("bottom", bottomOpen, bounds(QuizTags.BOTTOM))
        assertSame("input", inputOpen, bounds(QuizTags.INPUT))
        assertSame("prompt", promptOpen, bounds(QuizTags.PROMPT))
        compose.onNodeWithTag(QuizTags.INPUT, useUnmergedTree = true).assertIsFocused()
        assertTrue(Tip.TYPING in settings.tips.value.done)
    }

    // ---- result screen ----

    @Test
    fun resultTipComesOnlyOnceTheResultIsCompleteAndHighlightsPointsAndDetails() {
        launch(open = setOf(Tip.ROUND_RESULT))
        startRound()
        assertNoTipFor(1_000L)
        answerCorrectUntilFinished()

        // Never while the details (and the celebration, avatar growth and badges set with them) are missing.
        compose.waitUntil(ROUND_TIMEOUT) {
            val finished = quiz.round.value as? QuizRoundState.Finished
            if (exists(TipTags.CARD)) assertNotNull("tip before the result is complete", finished?.details)
            exists(TipTags.CARD)
        }
        waitForTip("Das hast du geschafft")
        val finished = quiz.round.value as QuizRoundState.Finished
        val hole = bounds(TipTags.HOLE)
        assertTrue("points line", hole.containsRect(textBounds(PracticeTexts.roundPoints(finished.roundPoints!!))))
        assertTrue("details", hole.containsRect(textBounds(RoundDetailTexts.TITLE)))
        // The stars and their headline stay dimmed.
        assertTrue(hole.top >= textBounds("Super gemacht!").bottom)
        understood()
        assertTrue(Tip.ROUND_RESULT in settings.tips.value.done)
    }

    // ---- "Dein Lernstand", its chain to "Dein Avatar" ----

    private fun cardOnScreen(): Boolean {
        val card = bounds(TipTags.CARD)
        var windowHeight = 0
        var statusBar = 0
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            windowHeight = view.height
            statusBar = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        return card.top >= statusBar && card.bottom <= windowHeight
    }

    @Test
    fun theLevelsTipChainsToTheAvatarTipAndTheClosingTipWaitsForTheNextVisit() {
        launch(open = setOf(Tip.ROUND_RESULT, Tip.LEVELS, Tip.AVATAR_CARD, Tip.ALL_KNOWN))
        assertNoTipFor()
        settings.tipUnderstood(Tip.ROUND_RESULT)

        waitForTip("Dein Lernstand")
        val hole = bounds(TipTags.HOLE)
        assertTrue("hole around the folded card", hole.containsRect(bounds(PROGRESS_CARD_TAG)))
        assertFalse("the card stays folded", quiz.progressExpanded.value)
        assertTrue("card fully on the screen", cardOnScreen())
        assertFalse("card beside the target", bounds(TipTags.CARD).overlaps(hole))
        understood()

        // The chain: in the same visit.
        waitForTip("Dein Avatar")
        understood()
        // The closing tip still waits for the next visit.
        assertNoTipFor()
        leaveAndReturn()
        waitForTip("Jetzt kennst du alles Wichtige")
    }

    @Test
    fun aDismissedLevelsTipComesBackAndTheAvatarTipWaits() {
        launch(open = setOf(Tip.LEVELS, Tip.AVATAR_CARD))
        waitForTip("Dein Lernstand")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertNoTipFor()

        leaveAndReturn()
        waitForTip("Dein Lernstand")
        assertFalse(Tip.LEVELS in settings.tips.value.done)
        assertFalse(Tip.AVATAR_CARD in settings.tips.value.done)
    }

    @Test
    fun theLevelsTipWaitsForTheVocabularyMode() {
        launch(open = setOf(Tip.LEVELS), verbs = verbs, mode = QuizMode.VERBS)
        assertNoTipFor()
        compose.runOnUiThread { quiz.selectMode(QuizMode.VOCABULARY) }
        waitForTip("Dein Lernstand")
    }

    // ---- "Verben üben" ----

    private val verbs = listOf("go" to "went", "see" to "saw", "take" to "took", "write" to "wrote")
        .map { (infinitive, past) -> IrregularVerb(infinitive = infinitive, simplePast = past, germanMeanings = listOf("x$infinitive"), unit = 1) }

    /** A mode tile by its exact label (not the tip card, not the selection line). */
    private fun tile(label: String) = hasText(label) and !hasAnyAncestor(hasTestTag(TipTags.CARD))

    @Test
    fun theVerbTipComesOnTheFirstTapOnVerbenAndHighlightsBothTiles() {
        // N2 would be due too, but it waits for the vocabulary mode; the verb tip does not use up the visit.
        launch(open = setOf(Tip.VERBS, Tip.START_BUTTON), verbs = verbs)
        waitForTip("Los geht's")
        understood()
        compose.onAllNodes(tile("Verben")).onFirst().performClick()

        waitForTip("Verben üben")
        val hole = bounds(TipTags.HOLE)
        for (label in listOf("Vokabeln", "Verben")) {
            val tileBounds = compose.onAllNodes(tile(label)).fetchSemanticsNodes().first().boundsInWindow
            assertTrue("hole around $label", hole.containsRect(tileBounds))
        }
        assertTrue(cardOnScreen())
        understood()
        assertTrue(Tip.VERBS in settings.tips.value.done)
    }

    @Test
    fun withoutVerbsTheVerbTipWaitsForALaterTap() {
        launch(open = setOf(Tip.VERBS))
        compose.onAllNodes(tile("Verben")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { textExists(StartTexts.NO_VERBS) }
        assertNoTipFor()

        compose.onAllNodes(tile("Vokabeln")).onFirst().performClick()
        runBlocking { db.irregularVerbDao().insertAll(verbs.inTestBook()) }
        compose.onAllNodes(tile("Verben")).onFirst().performClick()
        waitForTip("Verben üben")
    }

    @Test
    fun theStartTipWaitsForTheVocabularyMode() {
        launch(open = setOf(Tip.START_BUTTON), verbs = verbs, mode = QuizMode.VERBS)
        assertNoTipFor()
        compose.runOnUiThread { quiz.selectMode(QuizMode.VOCABULARY) }
        waitForTip("Los geht's")
    }

    // ---- "Wiederholung" ----

    /** Typed round: the first word wrong (then copied), the others right, until the first repetition is asked. */
    private fun answerUntilTheFirstRepeat() {
        val end = System.currentTimeMillis() + ROUND_TIMEOUT
        var wrongGiven = false
        while (asking?.question?.isRepeat != true) {
            val state = asking
            val typing = state?.typing
            val core = state?.question?.question?.typing?.coreWord
            if (typing != null && core != null) {
                when (typing.phase) {
                    TypingPhase.TYPING -> {
                        val input = if (wrongGiven) core else "qqqqq"
                        wrongGiven = true
                        compose.runOnUiThread {
                            quiz.typingInput(input)
                            quiz.submitTyped()
                        }
                    }
                    TypingPhase.COPYING -> compose.runOnUiThread {
                        quiz.typingInput(core)
                        quiz.submitTyped()
                    }
                    else -> Unit
                }
            }
            assertTrue("no repetition came", System.currentTimeMillis() < end)
            Thread.sleep(150)
        }
    }

    @Test
    fun theRepeatTipPointsAtWiederholungAndLeavesTheKeyboardAlone() {
        launch(open = setOf(Tip.REPEAT), words = words(3))
        startRound()
        // No tip on a first question.
        assertNoTipFor(1_000L)
        answerUntilTheFirstRepeat()
        compose.waitUntil(TIMEOUT) { exists(QuizTags.INPUT) && imeBottom() > 0 }
        waitForTip("Wiederholung")

        val hole = bounds(TipTags.HOLE)
        assertTrue("hole around the label", hole.containsRect(textBounds("Wiederholung")))
        assertFalse("the input is not highlighted", hole.containsRect(bounds(QuizTags.INPUT)))
        val card = bounds(TipTags.CARD)
        assertTrue("card above the keyboard", card.bottom <= compose.activity.window.decorView.height - imeBottom())
        assertFalse(card.overlaps(hole))
        compose.onNodeWithTag(QuizTags.INPUT, useUnmergedTree = true).assertIsFocused()

        val imeOpen = imeBottom()
        understood()
        compose.waitForIdle()
        assertEquals("keyboard", imeOpen, imeBottom())
        compose.onNodeWithTag(QuizTags.INPUT, useUnmergedTree = true).assertIsFocused()
        assertTrue(Tip.REPEAT in settings.tips.value.done)
    }

    @Test
    fun bookProgressAloneBringsNeitherTheLevelsTipNorTheAvatarTip() {
        launch(open = setOf(Tip.ROUND_RESULT, Tip.LEVELS, Tip.AVATAR_CARD))
        firstRound()
        assertNoTipFor()
        leaveAndReturn()
        assertNoTipFor()
    }
}
