package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The question screen with the real keyboard: the header and the word never move, "Prüfen"
 * and "Mit Vorschlägen" stay above the keyboard, and the word sits at the same place in typing questions and
 * questions with suggestions. Runs on an in-memory database with its own settings, so the installed app's data
 * is never touched. Run it with the system defaults of font size and display size: the app ignores both
 * ([FixedUiScale], checked by [FixedUiScaleScreenshotTest]), but this test's plain activity would not. The numbers
 * are logged with the tag [TAG], and screenshots of the key states go to [SHOT_DIR] on the device.
 */
@RunWith(AndroidJUnit4::class)
class QuestionLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings
    private val fontScale get() = context.resources.configuration.fontScale

    private companion object {
        const val TAG = "QuestionLayoutTest"
        const val SETTINGS = "test_question_layout_settings"
        const val DEVICE = "test_question_layout_device"
        const val SHOT_DIR = "/data/local/tmp/voctrainer_layout"

        /** Pixel tolerance for "identical" (layout positions are whole pixels; this only absorbs rounding). */
        const val SAME = 0.5f
    }

    // German prompt → English entry and core word. Level 2: German → English with suggestions;
    // level 3/4 with a core word: typed.
    // Level 0/1: English → German with suggestions (the speaker button is shown there, and only there).
    // The typed words have German meanings over two lines, so the correct word after a mistake must still fit.
    private val words = listOf(
        Vocabulary(english = "bread", germanMeanings = listOf("Brot"), unit = 1, leitnerLevel = 1),
        Vocabulary(english = "cheese", germanMeanings = listOf("Käse"), unit = 1, leitnerLevel = 0),
        Vocabulary(english = "milk", germanMeanings = listOf("Milch"), unit = 1, leitnerLevel = 1),
        Vocabulary(english = "apple", germanMeanings = listOf("Apfel"), unit = 1, leitnerLevel = 2, coreWord = "apple"),
        Vocabulary(english = "river", germanMeanings = listOf("Fluss"), unit = 1, leitnerLevel = 2, coreWord = "river"),
        Vocabulary(english = "chair", germanMeanings = listOf("Stuhl"), unit = 1, leitnerLevel = 2, coreWord = "chair"),
        Vocabulary(english = "garden", germanMeanings = listOf("Garten", "Gartenanlage", "Schrebergarten"), unit = 1, leitnerLevel = 3, coreWord = "garden"),
        Vocabulary(english = "window", germanMeanings = listOf("Fenster", "Fensterscheibe", "Schaufenster"), unit = 1, leitnerLevel = 3, coreWord = "window"),
        Vocabulary(english = "kitchen", germanMeanings = listOf("Küche", "Kochnische", "Küchenzeile", "Kochen"), unit = 1, leitnerLevel = 3, coreWord = "kitchen"),
        Vocabulary(english = "teacher", germanMeanings = listOf("Lehrer", "Lehrerin", "Lehrkraft", "Pädagoge"), unit = 1, leitnerLevel = 3, coreWord = "teacher"),
        Vocabulary(
            english = "bridge", germanMeanings = listOf("Brücke"), unit = 1, leitnerLevel = 4, highestLevel = 4,
            coreWord = "bridge"
        )
    )

    private data class Sample(
        val label: String,
        val prompt: Rect,
        val header: Rect,
        val bottom: Rect,
        val check: Rect?,
        val choice: Rect?,
        val keyboardTop: Float,
        val imeBottom: Int,
        val statusBar: Int,
        val typing: Boolean
    )

    private val samples = mutableListOf<Sample>()
    private val failures = mutableListOf<String>()
    private var englishToGerman = 0
    private var germanToEnglish = 0

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            markAllTipsDone()
        }
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            db.vocabularyDao().insertAll(words.inTestBook())
            val now = System.currentTimeMillis()
            db.figureDao().upsert(
                Figure(slot = 0, kind = FigureKind.entries.first().key, pointsAtStart = 0, startedAt = now, updatedAt = now, bookId = TEST_BOOK_ID)
            )
        }
        device.executeShellCommand("mkdir -p $SHOT_DIR")
        device.executeShellCommand("rm -f $SHOT_DIR/fs${fontScale}_*")
        compose.runOnUiThread {
            // As MainActivity: edge to edge, the app handles the keyboard insets itself.
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
    }

    @After
    fun tearDown() {
        VocTrainerDatabase.replaceInstanceForTest(null)
        AppSettings.replaceInstanceForTest(null)
        db.closeAfterRunningQueries()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    private val viewModel: QuizViewModel
        get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    private val asking: QuizRoundState.Asking?
        get() = viewModel.round.value as? QuizRoundState.Asking

    private fun word(state: QuizRoundState.Asking): Vocabulary =
        words.first { it.id == state.question.question.word.id }

    private fun isTyping(state: QuizRoundState.Asking?): Boolean =
        state?.typing?.let { it.phase != TypingPhase.CHOICE } == true

    private fun bounds(tag: String): Rect? =
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull()?.boundsInWindow

    /** IME bottom inset, status bar height and window height in px, read from the root window insets. */
    private fun insets(): Triple<Int, Int, Int> {
        var result = Triple(0, 0, 0)
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(view)
            result = Triple(
                insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0,
                insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0,
                view.height
            )
        }
        return result
    }

    /** Waits until the keyboard inset has not changed for a while (shown: > 0, hidden: 0). */
    private fun waitForKeyboard(shown: Boolean) {
        val end = System.currentTimeMillis() + 5000
        var last = -1
        var stableSince = System.currentTimeMillis()
        while (System.currentTimeMillis() < end) {
            // The test clock only draws frames while synced; the input field gives up the keyboard in a frame.
            if (!shown) compose.waitForIdle()
            val ime = insets().first
            if (ime != last) {
                last = ime
                stableSince = System.currentTimeMillis()
            } else if ((ime > 0) == shown && System.currentTimeMillis() - stableSince > 600) {
                return
            }
            Thread.sleep(50)
        }
        failures += "keyboard ${if (shown) "did not open" else "did not close"} (ime=$last)"
    }

    /** Measures the current state; [settled] = false while the keyboard may still be moving (no keyboard checks). */
    private fun sample(label: String, shot: Boolean = false, settled: Boolean = true): Sample {
        compose.waitForIdle()
        val (ime, status, height) = insets()
        val s = Sample(
            label = label,
            prompt = bounds(QuizTags.PROMPT)!!,
            header = bounds(QuizTags.HEADER)!!,
            bottom = bounds(QuizTags.BOTTOM)!!,
            check = bounds(QuizTags.CHECK),
            choice = bounds(QuizTags.CHOICE),
            keyboardTop = (height - ime).toFloat(),
            imeBottom = ime,
            statusBar = status,
            typing = isTyping(asking)
        )
        samples += s
        Log.i(
            TAG,
            "fs=$fontScale $label typing=${s.typing} promptTop=${s.prompt.top} promptLeft=${s.prompt.left} " +
                "headerTop=${s.header.top} headerBottom=${s.header.bottom} bottomGroup=${s.bottom.top}..${s.bottom.bottom} " +
                "checkBottom=${s.check?.bottom} choiceBottom=${s.choice?.bottom} keyboardTop=${s.keyboardTop} ime=$ime"
        )
        checkSpeaker(label, s.prompt)
        // Header and bottom group fully visible, above the keyboard.
        check(s.header.top >= status, "$label: header top ${s.header.top} under the status bar ($status)")
        check(s.header.bottom <= s.bottom.top, "$label: header overlaps the bottom group")
        if (shot) screenshot(label)
        if (!settled) return s
        // After a typing mistake the correct word below the frame is fully visible (not hidden by the answer part).
        if (s.typing && asking?.typing?.phase == TypingPhase.COPYING) {
            // boundsInWindow is clipped by the scrolling upper part; size is not. Clipped = smaller than its size.
            val feedback = compose.onAllNodes(hasTestTag(QuizTags.FEEDBACK)).fetchSemanticsNodes().firstOrNull()
            val visible = feedback?.boundsInWindow?.height ?: 0f
            val full = feedback?.size?.height?.toFloat() ?: 0f
            check(
                feedback != null && visible >= full - SAME,
                "$label: feedback slot cut off (visible height $visible of $full)"
            )
        }
        check(s.bottom.bottom <= s.keyboardTop + SAME, "$label: bottom group ${s.bottom.bottom} below keyboard top ${s.keyboardTop}")
        s.check?.let { check(it.bottom <= s.keyboardTop + SAME, "$label: Prüfen bottom ${it.bottom} > keyboard top ${s.keyboardTop}") }
        s.choice?.let { check(it.bottom <= s.keyboardTop + SAME, "$label: Mit Vorschlägen bottom ${it.bottom} > keyboard top ${s.keyboardTop}") }
        return s
    }

    /**
     * The speaker button exists only in English → German questions (the English word is the prompt) and only with an
     * English voice; in German → English questions the German prompt is horizontally centered on the screen.
     */
    private fun checkSpeaker(label: String, prompt: Rect) {
        val state = asking ?: return
        val englishPrompt = state.question.question.speakOnShow != null
        val speaker = bounds(QuizTags.SPEAKER)
        if (englishPrompt) {
            if (viewModel.speechAvailable.value) {
                englishToGerman++
                check(speaker != null, "$label: speaker button missing in an English → German question")
            }
        } else {
            germanToEnglish++
            check(speaker == null, "$label: speaker button shown in a German → English question")
            val screenCenter = bounds(QuizTags.HEADER)!!.let { (it.left + it.right) / 2 }
            val dp = context.resources.displayMetrics.density
            check(
                abs((prompt.left + prompt.right) / 2 - screenCenter) <= dp,
                "$label: prompt not centered (center ${(prompt.left + prompt.right) / 2}, screen $screenCenter)"
            )
        }
    }

    private fun check(ok: Boolean, message: String) {
        if (!ok) failures += message
    }

    private fun screenshot(label: String) {
        val name = "fs${fontScale}_${label}.png".replace(' ', '_')
        device.executeShellCommand("screencap -p $SHOT_DIR/$name")
    }

    private fun waitFor(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) { condition() }
    }

    /** Waits for the next question after [previous] (or the result screen). */
    private fun waitForNext(previous: QuizRoundState.Asking) {
        waitFor { val now = viewModel.round.value; now !is QuizRoundState.Asking || now.question !== previous.question }
    }

    private fun typeAndCheck(text: String) {
        compose.onNodeWithTag(QuizTags.INPUT).performTextReplacement(text)
        compose.onNodeWithTag(QuizTags.CHECK).performClick()
        compose.waitForIdle()
    }

    private fun chooseCorrect(state: QuizRoundState.Asking) {
        val correct = state.options[state.correctIndex]
        compose.onNode(hasText(correct) and hasClickAction()).performClick()
    }

    private fun chooseWrong(state: QuizRoundState.Asking) {
        val wrong = state.options.withIndex().first { it.index != state.correctIndex }.value
        compose.onNode(hasText(wrong) and hasClickAction()).performClick()
    }

    @Test
    fun wordHeaderAndButtonsStayInPlace() {
        // The speaker button appears once the speech engine is ready; wait for it, so it does not shift the first word.
        val end = System.currentTimeMillis() + 10_000
        while (!viewModel.speechAvailable.value && System.currentTimeMillis() < end) Thread.sleep(100)
        Log.i(TAG, "speechAvailable=${viewModel.speechAvailable.value}")
        compose.onNodeWithText("Los geht's").performClick()
        waitFor { asking != null }

        var typingQuestions = 0
        var suggestionQuestions = 0
        var lastWasTyping = false
        while (true) {
            val state = asking ?: break
            val english = word(state).english
            if (isTyping(state)) {
                typingQuestions++
                val n = typingQuestions
                val first = sample("t$n-$english-start", settled = lastWasTyping)
                // Prompt top during the keyboard animation (first question) or with the keyboard already open.
                val during = mutableListOf<Float>()
                repeat(30) {
                    during += bounds(QuizTags.PROMPT)!!.top
                    Thread.sleep(20)
                }
                check(during.all { abs(it - first.prompt.top) <= SAME }, "t$n: prompt moved while the keyboard opened: ${during.distinct()}")
                waitForKeyboard(shown = true)
                sample("t$n-$english-keyboard", shot = n == 1)
                val core = word(state).coreWord!!
                when (n) {
                    2 -> {
                        typeAndCheck("xyzxyz")
                        sample("t$n-$english-wrong", shot = true)
                        typeAndCheck("xyzxyz")
                        sample("t$n-$english-copy-mismatch")
                        typeAndCheck(core)
                        sample("t$n-$english-copied")
                    }
                    3 -> {
                        // One letter missing: "Fast richtig".
                        typeAndCheck(core.removeRange(2, 3))
                        sample("t$n-$english-almost", shot = true)
                        check(
                            asking?.typing?.almost != null,
                            "t$n: '${core.removeRange(2, 3)}' was not 'Fast richtig' (phase ${asking?.typing?.phase})"
                        )
                        // A wrong copy: reason, correct word and the hint must all stay visible.
                        typeAndCheck("xyzxyz")
                        sample("t$n-$english-almost-copy-mismatch", shot = true)
                        typeAndCheck(core)
                        sample("t$n-$english-copied")
                    }
                    else -> if (word(state).leitnerLevel >= 4 || n == 4) {
                        // Wait for "Mit Vorschlägen" and take it: the keyboard hides, the word stays.
                        waitFor { bounds(QuizTags.CHOICE) != null }
                        sample("t$n-$english-choice-button", shot = true)
                        compose.onNodeWithTag(QuizTags.CHOICE).performClick()
                        waitForKeyboard(shown = false)
                        sample("t$n-$english-choice-options", shot = true)
                        chooseCorrect(asking!!)
                        sample("t$n-$english-choice-correct")
                    } else {
                        typeAndCheck(core)
                        sample("t$n-$english-correct", shot = n == 1)
                    }
                }
                lastWasTyping = true
            } else {
                suggestionQuestions++
                val n = suggestionQuestions
                if (lastWasTyping) waitForKeyboard(shown = false)
                sample("s$n-$english-start", shot = lastWasTyping)
                if (n == 2) {
                    chooseWrong(state)
                    sample("s$n-$english-wrong", shot = true)
                    compose.onNodeWithText("Nochmal versuchen").performClick()
                    sample("s$n-$english-retry")
                    chooseCorrect(asking!!)
                } else {
                    chooseCorrect(state)
                }
                sample("s$n-$english-correct")
                lastWasTyping = false
            }
            waitForNext(state)
        }
        // The result screen: everything of the round is stored before the database is closed.
        waitFor { (viewModel.round.value as? QuizRoundState.Finished)?.roundPoints != null }
        screenshot("result")

        // The word never moves: one top and one left position for all states and both kinds of question. A prompt
        // over two lines is taller and starts higher (it is centred in the minimum height of its row), so only
        // prompts of the same height are compared.
        samples.groupBy { it.prompt.height }.values.forEach { group ->
            val tops = group.map { it.prompt.top }
            check(tops.max() - tops.min() <= SAME, "prompt top differs: ${group.groupBy({ it.prompt.top }, { it.label })}")
        }
        check(samples.map { it.prompt.height }.distinct().size > 1, "no prompt over two lines was checked")
        val headerTops = samples.map { it.header.top }
        check(headerTops.max() - headerTops.min() <= SAME, "header top differs: ${headerTops.distinct()}")
        // Same text in different states of one question: same left edge too.
        samples.groupBy { it.label.substringBefore('-') + it.label.split('-')[1] }.values.forEach { group ->
            val lefts = group.map { it.prompt.left }
            check(lefts.max() - lefts.min() <= SAME, "prompt left moved within ${group.first().label}: ${lefts.distinct()}")
        }
        // With the keyboard open, "Prüfen" and the bottom group keep their place within a typing question. Only
        // states with the same keyboard height are compared: the keyboard itself can change its height (Gboard does
        // that now and then), and then the bottom group follows it, as it should.
        samples.filter { it.typing && it.imeBottom > 0 && !it.label.endsWith("-start") }
            .groupBy { it.label.substringBefore('-') to it.imeBottom }.values.forEach { group ->
                val checks = group.mapNotNull { it.check }
                check(checks.distinct().size <= 1, "Prüfen moved within ${group.first().label}: ${checks.distinct()}")
                val tops = group.map { it.bottom.top }
                check(tops.distinct().size <= 1, "bottom group moved within ${group.first().label}: ${tops.distinct()}")
            }
        check(germanToEnglish > 0, "no German → English question was checked")
        check(typingQuestions >= 4, "only $typingQuestions typing questions")
        check(suggestionQuestions >= 3, "only $suggestionQuestions questions with suggestions")
        Log.i(TAG, "fs=$fontScale typing=$typingQuestions suggestions=$suggestionQuestions samples=${samples.size}")
        failures.forEach { Log.e(TAG, "fs=$fontScale FAIL $it") }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // Phrases are typed like words (core word in the gap); fewer than a round, so every one is asked.
    private val phrases = listOf(
        Vocabulary(
            english = "behind the old library on Park Road and Mill Lane", germanMeanings = listOf("hinter der alten Bücherei an der Park Road und Mill Lane"),
            unit = 1, leitnerLevel = 3, wordType = WordType.PHRASE, coreWord = "library"
        ),
        Vocabulary(
            english = "Have you ever …?", germanMeanings = listOf("Hast du jemals …?"), unit = 1, leitnerLevel = 1,
            wordType = WordType.PHRASE, coreWord = "ever"
        ),
        Vocabulary(english = "No way!", germanMeanings = listOf("Auf keinen Fall!"), unit = 1, leitnerLevel = 0, wordType = WordType.PHRASE, coreWord = "way"),
        Vocabulary(english = "See you soon!", germanMeanings = listOf("Bis bald!"), unit = 1, leitnerLevel = 0, wordType = WordType.PHRASE, coreWord = "soon"),
        Vocabulary(english = "Don't worry.", germanMeanings = listOf("Mach dir keine Sorgen."), unit = 1, leitnerLevel = 0, wordType = WordType.PHRASE, coreWord = "worry"),
        Vocabulary(english = "twice a month", germanMeanings = listOf("zweimal im Monat"), unit = 1, leitnerLevel = 0, wordType = WordType.PHRASE, coreWord = "twice")
    )

    private fun noRemovedNote(label: String) {
        val shown = compose.onAllNodes(hasText("Nur mit Vorschlägen", substring = true)).fetchSemanticsNodes()
        check(shown.isEmpty(), "$label: „Nur mit Vorschlägen“ is shown")
    }

    @Test
    fun phrasesKeepHeaderFrameAndButtonsInPlace() {
        runBlocking {
            db.vocabularyDao().deleteAll()
            db.vocabularyDao().insertAll(phrases.inTestBook())
        }
        waitFor { viewModel.startState.value.wordCount == phrases.size }
        compose.onNodeWithText("Los geht's").performClick()
        waitFor { asking != null }

        var typedLong = false
        var suggestedPhrase = false
        var lastWasTyping = false
        val frames = mutableListOf<Pair<String, Rect>>()
        while (true) {
            val state = asking ?: break
            val word = state.question.question.word
            val label = word.english.take(12)
            val own = mutableListOf<Sample>()
            if (isTyping(state)) {
                own += sample("p-$label-start", settled = false)
                waitForKeyboard(shown = true)
                own += sample("p-$label-keyboard", shot = word.coreWord == "library")
                noRemovedNote("p-$label-keyboard")
                if (word.coreWord == "library") {
                    typedLong = true
                    frames += "keyboard" to compose.onNode(hasText("on Park Road", substring = true)).fetchSemanticsNode().boundsInWindow
                }
                typeAndCheck(word.coreWord!!)
                // Measured before the sample: the next question follows 1 s after a correct answer, and the
                // screenshot of the sample can take that long on a slow emulator.
                if (word.coreWord == "library") {
                    frames += "correct" to compose.onNode(hasText("on Park Road", substring = true)).fetchSemanticsNode().boundsInWindow
                }
                own += sample("p-$label-correct", shot = word.coreWord == "library")
                // With the keyboard open, "Prüfen" and the bottom group keep their place before and after the answer.
                val open = own.filter { it.imeBottom > 0 && !it.label.endsWith("-start") }
                open.groupBy { it.imeBottom }.values.forEach { group ->
                    check(group.map { it.bottom.top }.distinct().size <= 1, "$label: bottom group moved: ${group.map { it.bottom.top }}")
                }
                lastWasTyping = true
            } else {
                if (lastWasTyping) waitForKeyboard(shown = false)
                own += sample("p-$label-start")
                noRemovedNote("p-$label-start")
                if (word.coreWord == "ever" && state.question.question.speakOnShow != null) suggestedPhrase = true
                chooseCorrect(state)
                own += sample("p-$label-correct")
                check(own.map { it.bottom.top }.distinct().size <= 1, "$label: bottom group moved: ${own.map { it.bottom.top }}")
                lastWasTyping = false
            }
            own.groupBy { it.prompt.height }.values.forEach { group ->
                check(group.map { it.prompt.top }.distinct().size <= 1, "$label: prompt moved: ${group.map { it.prompt.top }}")
            }
            waitForNext(state)
        }
        waitFor { (viewModel.round.value as? QuizRoundState.Finished)?.roundPoints != null }

        val headerTops = samples.map { it.header.top }
        check(headerTops.max() - headerTops.min() <= SAME, "header top differs: ${headerTops.distinct()}")
        check(frames.size == 2 && abs(frames[0].second.top - frames[1].second.top) <= SAME, "frame moved: $frames")
        check(typedLong, "the long phrase was not typed")
        check(suggestedPhrase, "no phrase was asked English → German with suggestions")
        failures.forEach { Log.e(TAG, "fs=$fontScale FAIL $it") }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
