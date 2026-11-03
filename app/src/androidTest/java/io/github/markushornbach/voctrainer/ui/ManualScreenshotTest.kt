package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inMemoryDatabase
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.TipState
import io.github.markushornbach.voctrainer.settings.setTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a test of behaviour: takes the screenshots of the user manual (`docs/images/`) with the example
 * book, the default theme „Ozean“ in light mode, no tips and the look of the release app
 * ([AppLook.testMarkers] off). The files land in `files/manual_screenshots` of the app's external
 * storage, already scaled to [WIDTH] px; pull them with adb into `docs/images/`. Run it with the
 * system defaults of font size and display size (see [TipGalleryScreenshotTest]).
 */
@RunWith(AndroidJUnit4::class)
class ManualScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_manual_settings"
        const val DEVICE = "test_manual_device"
        const val TIMEOUT = 20_000L
        const val ROUND_TIMEOUT = 120_000L
        const val SETTLE_MS = 1_500L
        const val WIDTH = 420
        const val DAY_MS = 24L * 60 * 60 * 1000
    }

    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
    private val asking: QuizRoundState.Asking? get() = quiz.round.value as? QuizRoundState.Asking

    @Before
    fun setUp() {
        clearPrefs()
        AppLook.testMarkers = false
        db = inMemoryDatabase(context)
        repository = VocabularyRepository(db)
        VocTrainerDatabase.replaceInstanceForTest(db)
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        VocTrainerDatabase.replaceInstanceForTest(null)
        AppSettings.replaceInstanceForTest(null)
        AppLook.testMarkers = true
        db.closeAfterRunningQueries()
        clearPrefs()
    }

    private fun clearPrefs() {
        listOf(SETTINGS, DEVICE).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
    }

    // ---- helpers ----

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Full-screen screenshot, scaled to [WIDTH] px, into `files/manual_screenshots`. */
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
        val full = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val scaled = Bitmap.createScaledBitmap(full, WIDTH, full.height * WIDTH / full.width, true)
        val dir = File(context.getExternalFilesDir(null), "manual_screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The app in light mode, whatever the emulator is set to. */
    private fun setApp() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            val light = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides light) {
                key(0) { VocTrainerTheme { VocTrainerApp() } }
            }
        }
    }

    private fun changeWords(transform: (Vocabulary) -> Vocabulary) = runBlocking {
        val dao = db.vocabularyDao()
        dao.updateAll(dao.getAllOfBookIncludingDeleted(ExampleBook.ID).map(transform))
    }.also {
        compose.waitForIdle()
        Thread.sleep(2_000L)
        compose.waitForIdle()
    }

    private fun startRound() {
        compose.runOnUiThread { quiz.startRound() }
        compose.waitUntil(TIMEOUT) { asking != null }
    }

    /** Answers every question correctly, by suggestion or by typing the core word. */
    private fun answerCorrectUntilFinished() {
        val end = System.currentTimeMillis() + ROUND_TIMEOUT
        while (quiz.round.value !is QuizRoundState.Finished) {
            val state = asking
            val typing = state?.typing
            val core = state?.question?.question?.typing?.coreWord
            if (typing != null && core != null && typing.phase == TypingPhase.TYPING) {
                compose.runOnUiThread {
                    quiz.typingInput(core)
                    quiz.submitTyped()
                }
            } else if (state != null && typing == null && state.phase == AnswerPhase.ANSWERING) {
                compose.runOnUiThread { quiz.answer(state.correctIndex) }
            }
            assertTrue("round did not finish", System.currentTimeMillis() < end)
            Thread.sleep(150)
        }
    }

    // ---- the screenshots ----

    @Test
    fun bookChoice() {
        settings = AppSettings(context, SETTINGS, DEVICE).apply { setAutoSpeak(false) }
        AppSettings.replaceInstanceForTest(settings)
        setApp()
        compose.waitUntil(TIMEOUT) { exists(FirstStartTags.WELCOME) }
        compose.onNodeWithText(FirstStartTexts.START).performClick()
        compose.waitUntil(TIMEOUT) { exists(FirstStartTags.BOOK_CHOICE) }
        shot("01_buchwahl")
    }

    /** Selection, a question with suggestions, the result, the start screen, „Mein Avatar“ and a typing question. */
    @Test
    fun practising() {
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            finishFirstStart()
            setTipsDone(Tip.entries.toSet())
        }
        AppSettings.replaceInstanceForTest(settings)
        setApp()
        compose.waitUntil(TIMEOUT) { textExists(FigureTexts.choiceTitle(false)) }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }
        compose.waitUntil(TIMEOUT) { runBlocking { db.vocabularyDao().getAllOfBookIncludingDeleted(ExampleBook.ID).isNotEmpty() } }

        // Some progress from earlier days: a few words learned, a few in practice, points for stage 2.
        val yesterday = System.currentTimeMillis() - DAY_MS
        var learned = 0
        var practised = 0
        changeWords { word ->
            when {
                word.unit != 1 -> word
                word.part == null && learned < 7 -> {
                    learned++
                    word.copy(leitnerLevel = 4, highestLevel = 4, lastReviewedAt = yesterday)
                }
                word.part == "A" && practised < 5 -> {
                    practised++
                    word.copy(leitnerLevel = 2, highestLevel = 2, lastReviewedAt = yesterday)
                }
                else -> word
            }
        }
        runBlocking {
            repository.addTestPoints(AppClock.today().minusDays(1).toString(), settings.deviceId, 140)
            repository.updateFigureStage()
        }

        // The selection: Unit 1, Einstieg and A.
        compose.runOnUiThread {
            quiz.openSelection()
            quiz.selectUnit(UnitChoice.Number(1))
            quiz.togglePart(null)
            quiz.togglePart("A")
        }
        shot("03_auswahl")
        compose.runOnUiThread { quiz.applySelection() }

        // A round of new words with suggestions.
        startRound()
        compose.waitUntil(TIMEOUT) { asking?.phase == AnswerPhase.ANSWERING }
        shot("04_frage_vorschlaege")
        answerCorrectUntilFinished()
        compose.waitUntil(TIMEOUT) { textExists("Zum Start") }
        shot("06_ergebnis")

        compose.onNodeWithText("Zum Start").performClick()
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) }
        shot("02_startseite")

        compose.onNodeWithTag(STAGE_BAR_TAG, useUnmergedTree = true).performClick()
        compose.waitUntil(TIMEOUT) { textExists("Meine Sammlung") }
        shot("07_avatar")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) }

        // A typing question with the first letters typed.
        changeWords { word ->
            if (word.unit == 1 && word.leitnerLevel < 3) word.copy(leitnerLevel = 3, highestLevel = 3, lastReviewedAt = yesterday) else word
        }
        startRound()
        compose.waitUntil(TIMEOUT) { asking?.typing != null && exists(QuizTags.INPUT) }
        Thread.sleep(1_000L)
        val core = asking?.question?.question?.typing?.coreWord.orEmpty()
        compose.runOnUiThread { quiz.typingInput(core.take(core.length / 2)) }
        shot("05_frage_tippen")
    }
}
