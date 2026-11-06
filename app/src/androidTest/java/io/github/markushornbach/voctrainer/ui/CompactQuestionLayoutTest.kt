package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.IrregularVerb
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.VerbFormMode
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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Sets the display size before the activity starts and always restores it. Tests whose name starts with "normal" run
 * at the size of a Pixel 8a (915 dp), all others at 731 dp. It changes a system setting, so it runs only on an emulator.
 */
private class DisplaySizeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val normal = description.methodName.startsWith("normal")
            try {
                device.executeShellCommand("wm size ${if (normal) "1080x2400" else "1080x1920"}")
                device.executeShellCommand("wm density 420")
                Thread.sleep(2000)
                base.evaluate()
            } finally {
                device.executeShellCommand("wm size reset")
                device.executeShellCommand("wm density reset")
                Thread.sleep(1000)
            }
        }
    }
}

/**
 * The compact question layout in a low window, with the real keyboard, and that a normal window has none of it.
 * In-memory database and own settings, so the installed app's data is never touched. The heights are logged with the
 * tag [TAG].
 */
@RunWith(AndroidJUnit4::class)
class CompactQuestionLayoutTest {
    @get:Rule(order = 0)
    val displaySize: TestRule = DisplaySizeRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings
    private val decisions = mutableListOf<Boolean>()

    private companion object {
        const val TAG = "CompactQuestionLayout"
        const val SETTINGS = "test_compact_question_settings"
        const val DEVICE = "test_compact_question_device"
        const val TIMEOUT = 15_000L
        const val SAME = 0.5f
        const val LONG_PROMPT = "sich zusammenreißen"
    }

    private fun filler() = listOf(
        Vocabulary(english = "bread", germanMeanings = listOf("Brot"), unit = 9, leitnerLevel = 0),
        Vocabulary(english = "cheese", germanMeanings = listOf("Käse"), unit = 9, leitnerLevel = 0),
        Vocabulary(english = "milk", germanMeanings = listOf("Milch"), unit = 9, leitnerLevel = 0)
    )

    private val longPhrase = Vocabulary(
        english = "to pull yourself together",
        germanMeanings = listOf("sich zusammenreißen", "sich nicht gehen lassen", "sich beherrschen", "die Fassung bewahren"),
        unit = 1, leitnerLevel = 2, wordType = WordType.PHRASE, coreWord = "together"
    )
    private val typedWord = Vocabulary(english = "river", germanMeanings = listOf("Fluss"), unit = 1, leitnerLevel = 3, coreWord = "river")
    private val typedLongPhrase = longPhrase.copy(leitnerLevel = 3)

    private val verbs = listOf(
        Triple("(to) go", "went", "gone"),
        Triple("(to) take", "took", "taken"),
        Triple("(to) see", "saw", "seen"),
        Triple("(to) make", "made", "made"),
        Triple("(to) write", "wrote", "written")
    ).mapIndexed { i, (infinitive, past, participle) ->
        IrregularVerb(
            infinitive = infinitive, simplePast = past, pastParticiple = participle,
            germanMeanings = listOf("x$infinitive"), unit = if (i == 0) 1 else 9, leitnerLevel = 2
        )
    }

    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
    private val asking: QuizRoundState.Asking? get() = quiz.round.value as? QuizRoundState.Asking
    private val dp: Float get() = context.resources.displayMetrics.density

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            finishFirstStart()
            setTipsDone(Tip.entries.toSet())
        }
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            val now = System.currentTimeMillis()
            db.figureDao().upsert(
                Figure(slot = 0, kind = FigureKind.entries.first().key, pointsAtStart = 0, startedAt = now, updatedAt = now, bookId = TEST_BOOK_ID)
            )
        }
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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

    private fun launchWords(target: Vocabulary) {
        runBlocking { db.vocabularyDao().insertAll((listOf(target) + filler()).inTestBook()) }
        settings.setQuizMode(QuizMode.VOCABULARY)
        launch()
    }

    private fun launchVerbs(forms: VerbFormMode) {
        runBlocking { db.irregularVerbDao().insertAll(verbs.inTestBook()) }
        settings.setVerbFormMode(forms)
        settings.setQuizMode(QuizMode.VERBS)
        launch()
    }

    /** Starts a round with exactly the one word of unit 1 (the others only provide the wrong answers). */
    private fun launch() {
        compose.setContent {
            VocTrainerTheme {
                val compact = rememberIsCompactHeight()
                SideEffect { decisions += compact }
                VocTrainerApp()
            }
        }
        compose.waitUntil(TIMEOUT) { quiz.startState.value.loaded && quiz.startState.value.wordCount > 0 }
        compose.runOnUiThread { quiz.selectUnit(UnitChoice.Number(1)) }
        compose.waitUntil(TIMEOUT) { quiz.startState.value.wordCount == 1 }
        compose.runOnUiThread { quiz.startRound() }
        compose.waitUntil(TIMEOUT) { asking != null }
        compose.waitForIdle()
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInWindow

    private fun textInTop(text: String): SemanticsNode = compose.onAllNodes(
        hasText(text, substring = true) and hasAnyAncestor(hasTestTag(QuizTags.TOP)),
        useUnmergedTree = true
    ).fetchSemanticsNodes().first()

    /** boundsInWindow is clipped by the scrolling upper part, size is not. */
    private fun assertFullyVisible(what: String, node: SemanticsNode) {
        val visible = node.boundsInWindow.height
        assertTrue("$what cut off: $visible of ${node.size.height}", visible >= node.size.height - SAME)
        assertTrue("$what scrolled out of view", visible > 0f)
    }

    private fun ime(): Int {
        var value = 0
        compose.runOnUiThread {
            value = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        return value
    }

    private fun waitForKeyboard() {
        val end = System.currentTimeMillis() + 8000
        var last = -1
        var since = System.currentTimeMillis()
        while (System.currentTimeMillis() < end) {
            compose.waitForIdle()
            val now = ime()
            if (now != last) {
                last = now
                since = System.currentTimeMillis()
            } else if (now > 0 && System.currentTimeMillis() - since > 600) {
                return
            }
            Thread.sleep(50)
        }
        throw AssertionError("the keyboard did not open (ime=$last)")
    }

    private fun typeWrong() {
        compose.onNodeWithTag(QuizTags.INPUT).performTextReplacement("xyzxyz")
        compose.onNodeWithTag(QuizTags.CHECK).performClick()
        compose.waitUntil(TIMEOUT) { asking?.typing?.phase == TypingPhase.COPYING }
        compose.waitForIdle()
        // The auto-scroll of the upper part.
        Thread.sleep(500)
        compose.waitForIdle()
    }

    private fun wrongIndex(state: QuizRoundState.Asking) = (state.correctIndex + 1) % state.options.size

    private fun answer(index: Int) {
        compose.runOnUiThread { quiz.answer(index) }
        compose.waitForIdle()
    }

    private fun log(label: String) = Log.i(TAG, "$label top=${bounds(QuizTags.TOP).height / dp} dp")

    private fun fadeEdgeNodes(): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(CompactFadeEdgeKey), useUnmergedTree = true).fetchSemanticsNodes()

    // (a) The decision.

    @Test
    fun theLowWindowIsCompactAlsoWithTheKeyboardOpen() {
        launchWords(typedWord)
        waitForKeyboard()
        assertTrue(decisions.isNotEmpty())
        assertTrue("decided by something else than the window: $decisions", decisions.all { it })
        assertFalse("the top bar is hidden during a question", exists(BOOK_TITLE_TAG))
        assertTrue("the slim header is about 40 dp", bounds(QuizTags.HEADER).height <= 41 * dp)
    }

    @Test
    fun normalWindowIsNotCompactAlsoWithTheKeyboardOpen() {
        launchWords(typedWord)
        waitForKeyboard()
        assertTrue(decisions.isNotEmpty())
        assertTrue("decided by something else than the window: $decisions", decisions.none { it })
    }

    // (b) The label of the verb form.

    @Test
    fun verbLabelStandsAboveTheAnswersInBothSteps() {
        launchVerbs(VerbFormMode.THREE)
        val first = asking!!
        assertLabel(VerbStepTexts.SIMPLE_PAST_LABEL)
        assertFalse("the line is replaced by the label", compose.onAllNodes(hasText("Wie lauten die Formen?")).fetchSemanticsNodes().isNotEmpty())
        assertTrue("the steps stay as information", exists(QuizTags.VERB_STEPS))
        val labelStep1 = bounds(QuizTags.VERB_FORM_LABEL)
        val bottomStep1 = bounds(QuizTags.BOTTOM)

        answer(wrongIndex(first))
        compose.waitUntil(TIMEOUT) { asking?.phase == AnswerPhase.SOLUTION }
        assertLabel(VerbStepTexts.SIMPLE_PAST_LABEL)
        assertEquals("the label moved after the wrong answer", labelStep1, bounds(QuizTags.VERB_FORM_LABEL))
        assertEquals("the answers moved after the wrong answer", bottomStep1, bounds(QuizTags.BOTTOM))
        assertFullyVisible("the solution", textInTop(first.solution))
        log("verb wrong")

        compose.runOnUiThread { quiz.retry() }
        compose.waitUntil(TIMEOUT) { asking?.phase == AnswerPhase.RETRY }
        answer(asking!!.correctIndex)
        compose.waitUntil(TIMEOUT) { asking?.inSecondStep == true }
        compose.waitForIdle()
        assertLabel(VerbStepTexts.PAST_PARTICIPLE_LABEL)
        assertEquals("the label jumped between the steps", labelStep1, bounds(QuizTags.VERB_FORM_LABEL))

        answer(wrongIndex(asking!!))
        compose.waitUntil(TIMEOUT) { asking?.phase == AnswerPhase.SOLUTION }
        assertLabel(VerbStepTexts.PAST_PARTICIPLE_LABEL)
        assertEquals("the label moved in step 2", labelStep1, bounds(QuizTags.VERB_FORM_LABEL))
        assertFullyVisible("the solution of step 2", textInTop(asking!!.solution))
    }

    @Test
    fun verbLabelAlsoWithTwoForms() {
        launchVerbs(VerbFormMode.TWO)
        assertLabel(VerbStepTexts.SIMPLE_PAST_LABEL)
        assertFalse(compose.onAllNodes(hasText("Wie lautet das simple past?")).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertLabel(text: String) {
        compose.onNode(hasTestTag(QuizTags.VERB_FORM_LABEL) and hasText(text), useUnmergedTree = true).assertExists()
        val label = bounds(QuizTags.VERB_FORM_LABEL)
        val bottom = bounds(QuizTags.BOTTOM)
        assertEquals("directly at the start of the answer part", bottom.top, label.top, SAME)
        assertTrue("visible", label.height >= 20 * dp)
    }

    // (c) Nothing moves.

    @Test
    fun nothingMovesWithKeyboardAndFeedback() {
        launchWords(typedWord)
        val promptBefore = bounds(QuizTags.PROMPT)
        val headerBefore = bounds(QuizTags.HEADER)
        waitForKeyboard()
        assertEquals("the word moved with the keyboard", promptBefore, bounds(QuizTags.PROMPT))
        assertEquals("the header moved with the keyboard", headerBefore, bounds(QuizTags.HEADER))
        val bottom = bounds(QuizTags.BOTTOM)
        val input = bounds(QuizTags.INPUT)
        val keyboardTop = keyboardTop()
        assertTrue("the input is above the keyboard", bottom.bottom <= keyboardTop + SAME)

        typeWrong()
        assertEquals("the word moved with the feedback", promptBefore, bounds(QuizTags.PROMPT))
        assertEquals("the answer part moved with the feedback", bottom, bounds(QuizTags.BOTTOM))
        assertEquals("the input moved with the feedback", input, bounds(QuizTags.INPUT))
    }

    @Test
    fun answersDoNotMoveWithTheFeedback() {
        launchWords(longPhrase)
        val prompt = bounds(QuizTags.PROMPT)
        val bottom = bounds(QuizTags.BOTTOM)
        answer(wrongIndex(asking!!))
        compose.waitUntil(TIMEOUT) { asking?.phase == AnswerPhase.SOLUTION }
        // The shake of the wrong answer does not change the group.
        Thread.sleep(500)
        compose.waitForIdle()
        assertEquals(prompt, bounds(QuizTags.PROMPT))
        assertEquals(bottom, bounds(QuizTags.BOTTOM))
        compose.onNode(hasText("Nochmal versuchen"), useUnmergedTree = true).assertExists()
        assertEquals("the retry button fits into its reserved place", bottom, bounds(QuizTags.BOTTOM))
    }

    private fun keyboardTop(): Float {
        var height = 0
        compose.runOnUiThread { height = compose.activity.window.decorView.height }
        return (height - ime()).toFloat()
    }

    // (d) The solution is visible.

    @Test
    fun longTextAndSolutionWithSuggestions() {
        launchWords(longPhrase)
        val lineHeight = 32 * dp
        assertTrue(
            "at least 4 lines of the question: top ${bounds(QuizTags.TOP).height / dp} dp",
            bounds(QuizTags.TOP).height >= 4 * lineHeight
        )
        assertFullyVisible("the question", textInTop(LONG_PROMPT))
        log("choice long")
        val state = asking!!
        answer(wrongIndex(state))
        compose.waitUntil(TIMEOUT) { asking?.phase == AnswerPhase.SOLUTION }
        assertFullyVisible("the solution", textInTop(state.solution))
        assertFullyVisible("the question after the answer", textInTop(LONG_PROMPT))
    }

    @Test
    fun copyInstructionIsVisibleWithTheKeyboardOpen() {
        launchWords(typedWord)
        waitForKeyboard()
        log("typing keyboard")
        assertTrue("the upper part is at least 110 dp high", bounds(QuizTags.TOP).height >= 110 * dp)
        typeWrong()
        assertFullyVisible("the correct word", textInTop(SOLUTION_LABEL))
        assertFullyVisible("the copy instruction", textInTop(COPY_INSTRUCTION))
    }

    @Test
    fun longPromptScrollsToTheCopyInstructionAndStaysReachable() {
        launchWords(typedLongPhrase)
        waitForKeyboard()
        log("typing long keyboard")
        typeWrong()
        assertFullyVisible("the correct word", textInTop(SOLUTION_LABEL))
        assertFullyVisible("the copy instruction", textInTop(COPY_INSTRUCTION))
        val bottom = bounds(QuizTags.BOTTOM)
        // The German text stays reachable by scrolling; only the upper part scrolls.
        compose.onNodeWithTag(QuizTags.PROMPT, useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        assertTrue("the question comes back into view", textInTop(LONG_PROMPT).boundsInWindow.height > 0f)
        assertEquals("the input never scrolls", bottom, bounds(QuizTags.BOTTOM))
    }

    @Test
    fun fadeEdgeWhileMoreContentLiesBelow() {
        launchWords(typedLongPhrase)
        waitForKeyboard()
        assertEquals(true, fadeEdgeNodes().single().config.getOrNull(CompactFadeEdgeKey))
    }

    @Test
    fun noFadeEdgeWhenEverythingFits() {
        launchWords(longPhrase)
        assertEquals(false, fadeEdgeNodes().single().config.getOrNull(CompactFadeEdgeKey))
    }

    // (e) A normal window has none of it.

    @Test
    fun normalWindowHasNoCompactElements() {
        launchVerbs(VerbFormMode.THREE)
        assertFalse("no label", exists(QuizTags.VERB_FORM_LABEL))
        assertTrue("no fade edge", fadeEdgeNodes().isEmpty())
        assertTrue("the top bar stays", exists(BOOK_TITLE_TAG))
        compose.onNode(hasText("Wie lauten die Formen?"), useUnmergedTree = true).assertExists()
        assertTrue("the steps stay", exists(QuizTags.VERB_STEPS))
        assertEquals("the header stays 48 dp high", 48 * dp, bounds(QuizTags.HEADER).height, 1f)
    }

    @Test
    fun normalWindowHasNoFadeEdgeWhenTyping() {
        launchWords(typedWord)
        waitForKeyboard()
        assertTrue(fadeEdgeNodes().isEmpty())
        assertTrue(exists(BOOK_TITLE_TAG))
    }
}
