package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.IrregularVerb
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.TipState
import io.github.markushornbach.voctrainer.settings.VerbFormMode
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

/**
 * "3 Formen": the setting on the page "Üben" and a verb round with both steps, a wrong first step
 * and its repetition at the end of the round. Verbs of the example book, in-memory database, own settings.
 */
@RunWith(AndroidJUnit4::class)
class ThreeVerbFormsUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_three_forms_settings"
        const val DEVICE = "test_three_forms_device"
        const val TIMEOUT = 15_000L
        const val ROUND_TIMEOUT = 120_000L
        const val START_LEVEL = 2
    }

    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    // From the example book; "(to) ride a bike" has no past participle.
    private val verbs = listOf(
        Triple("(to) go", "went", "gone"),
        Triple("(to) take", "took", "taken"),
        Triple("(to) see", "saw", "seen"),
        Triple("(to) make", "made", "made"),
        Triple("(to) put", "put", "put"),
        Triple("(to) ride a bike", "rode a bike", null)
    ).map { (infinitive, past, participle) ->
        IrregularVerb(
            infinitive = infinitive, simplePast = past, pastParticiple = participle,
            germanMeanings = listOf("x$infinitive"), unit = 1, leitnerLevel = START_LEVEL
        )
    }

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

    private fun launch(forms: VerbFormMode) {
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            setQuizMode(QuizMode.VERBS)
            setVerbFormMode(forms)
            finishFirstStart()
            setTipsDone(Tip.entries.toSet())
        }
        AppSettings.replaceInstanceForTest(settings)
        runBlocking { db.irregularVerbDao().insertAll(verbs.inTestBook()) }
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        compose.waitUntil(TIMEOUT) { quiz.startState.value.loaded && quiz.startState.value.wordCount > 0 }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private val asking: QuizRoundState.Asking? get() = quiz.round.value as? QuizRoundState.Asking

    private fun waitFor(condition: (QuizRoundState.Asking) -> Boolean): QuizRoundState.Asking {
        compose.waitUntil(TIMEOUT) { asking?.let(condition) == true }
        return asking!!
    }

    private fun tap(index: Int) {
        compose.runOnUiThread { quiz.answer(index) }
        compose.waitForIdle()
    }

    private fun level(infinitive: String): Int =
        runBlocking { db.irregularVerbDao().getAllIncludingDeleted() }.first { it.infinitive == infinitive }.leitnerLevel

    @Test
    fun switchingTheVerbFormsOnThePageUeben() {
        launch(VerbFormMode.TWO)
        assertEquals(verbs.size, quiz.startState.value.wordCount)
        compose.onAllNodes(hasContentDescription("Einstellungen")).onFirst().performClick()
        compose.onNodeWithText("Verbformen: 2 Formen").assertExists()
        compose.onNodeWithText(LearningSettingsTexts.TITLE).performClick()
        compose.onNodeWithText(LearningSettingsTexts.VERB_FORMS).assertExists()
        compose.onNodeWithText(VerbFormMode.TWO.label).assertExists()

        compose.onNodeWithTag(LearningSettingsTexts.tag(VerbFormMode.THREE)).performClick()
        compose.waitUntil(TIMEOUT) { settings.verbFormMode.value == VerbFormMode.THREE }
        // The verb without a past participle is left out; the levels stay as they are.
        compose.waitUntil(TIMEOUT) { quiz.startState.value.wordCount == verbs.size - 1 }
        assertTrue(quiz.startState.value.threeVerbForms)
        assertEquals(verbs.size - 1, quiz.startState.value.progress.countsPerLevel[START_LEVEL])

        compose.onNodeWithTag(LearningSettingsTexts.tag(VerbFormMode.TWO)).performClick()
        compose.waitUntil(TIMEOUT) { quiz.startState.value.wordCount == verbs.size }
        assertEquals(VerbFormMode.TWO, settings.verbFormMode.value)
        compose.onAllNodes(hasContentDescription("Zurück")).onFirst().performClick()
        compose.onNodeWithText("Verbformen: 2 Formen").assertExists()
        assertTrue(verbs.all { level(it.infinitive) == START_LEVEL })
    }

    @Test
    fun aRoundWithThreeFormsAndAWrongFirstStep() {
        launch(VerbFormMode.THREE)
        assertEquals(verbs.size - 1, quiz.startState.value.wordCount)
        compose.runOnUiThread { quiz.startRound() }
        val first = waitFor { true }
        val firstWord = first.question.question.word
        assertNotNull(first.question.question.secondStep)
        assertFalse(firstWord.english == "(to) ride a bike")
        assertTrue(exists(QuizTags.VERB_STEPS))
        assertTrue(textExists(VerbStepTexts.SIMPLE_PAST))
        assertTrue(textExists(VerbStepTexts.PAST_PARTICIPLE))
        assertTrue(textExists("Wie lauten die Formen?"))
        val forms = firstWord.verbForms!!

        // Step 1 wrong: the solution, "Nochmal versuchen", found again, then step 2 anyway.
        tap((first.correctIndex + 1) % first.options.size)
        waitFor { it.phase == AnswerPhase.SOLUTION }
        assertTrue(textExists("${firstWord.english} – ${forms.simplePast}"))
        compose.onNodeWithText("Nochmal versuchen").performClick()
        val retry = waitFor { it.phase == AnswerPhase.RETRY }
        tap(retry.correctIndex)
        val second = waitFor { it.inSecondStep }
        assertEquals(AnswerPhase.ANSWERING, second.phase)
        assertEquals(forms.pastParticiple, second.options[second.correctIndex])
        // The found simple past stays visible in its line.
        assertTrue(compose.onAllNodes(hasText(forms.simplePast) and hasAnyAncestor(hasTestTag(QuizTags.VERB_STEPS)), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())

        // Step 2 right: no level-up, one sentence why.
        tap(second.correctIndex)
        compose.waitUntil(TIMEOUT) { exists(QuizTags.FIRST_STEP_MISSED) }
        compose.onNodeWithText(VerbStepTexts.FIRST_STEP_MISSED).assertExists()
        compose.waitUntil(TIMEOUT) { level(firstWord.english) == START_LEVEL - 1 }

        // All other verbs right in both steps, until the wrong one comes again.
        val end = System.currentTimeMillis() + ROUND_TIMEOUT
        while (asking?.question?.isRepeat != true) {
            val state = asking
            if (state != null && state.phase == AnswerPhase.ANSWERING && state.question.question.word.id != firstWord.id) {
                tap(state.correctIndex)
            }
            assertTrue("no repetition came", System.currentTimeMillis() < end)
            Thread.sleep(150)
        }
        val repeat = asking!!
        assertEquals(firstWord.id, repeat.question.question.word.id)
        assertNotNull(repeat.question.question.secondStep)
        assertFalse(repeat.inSecondStep)

        // The repetition: both steps again; it changes no level.
        tap(repeat.correctIndex)
        val repeatSecond = waitFor { it.inSecondStep && it.phase == AnswerPhase.ANSWERING }
        tap(repeatSecond.correctIndex)
        compose.waitUntil(ROUND_TIMEOUT) { quiz.round.value is QuizRoundState.Finished }
        val finished = quiz.round.value as QuizRoundState.Finished
        assertEquals(verbs.size - 1, finished.totalWords)
        assertEquals(verbs.size - 2, finished.correctOnFirstAttempt)
        assertEquals(listOf(firstWord.id), finished.wrongWords.map { it.id })
        assertEquals(START_LEVEL - 1, level(firstWord.english))
        assertEquals(START_LEVEL, level("(to) ride a bike"))
    }
}
