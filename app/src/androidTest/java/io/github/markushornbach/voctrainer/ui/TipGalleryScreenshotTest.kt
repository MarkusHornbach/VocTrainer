package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.key
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Review material, not a test of behaviour: takes a screenshot of every tip (N1–N5, "Dein
 * Lernstand" with its chain, "Verben üben", "Selbst tippen", "Wiederholung"), the welcome screen, the book choice and the hint after the example book, each in its real
 * situation (example book, in-memory database, own settings; no user data is touched). The files land in `files/tip_gallery` of the app's external
 * storage; pull them with adb into a local folder.
 * Run it with the system defaults of font size and display size: the app always looks like that ([FixedUiScale]),
 * but this test's plain activity would follow the system settings.
 */
@RunWith(AndroidJUnit4::class)
class TipGalleryScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_tip_gallery_settings"
        const val DEVICE = "test_tip_gallery_device"
        const val TIMEOUT = 20_000L
        const val ROUND_TIMEOUT = 120_000L

        /** The spotlight settles in steps and fades in; a screenshot before that shows it half way. */
        const val SETTLE_MS = 1_500L
    }

    private val quiz: QuizViewModel get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
    private val asking: QuizRoundState.Asking? get() = quiz.round.value as? QuizRoundState.Asking

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
        listOf(SETTINGS, DEVICE).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
    }

    // ---- helpers ----

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun cardShows(title: String) =
        compose.onAllNodes(hasText(title) and hasAnyAncestor(hasTestTag(TipTags.CARD))).fetchSemanticsNodes().isNotEmpty()

    /** Full-screen screenshot (status bar, keyboard and all) into `files/tip_gallery`. */
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(context.getExternalFilesDir(null), "tip_gallery").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shotTip(name: String, title: String) {
        compose.waitUntil(TIMEOUT) { exists(TipTags.CARD) && cardShows(title) }
        shot(name)
    }

    /** The app on a fresh installation after the first start: the example book, the dragon, [open] as the only open tips. */
    private fun launchApp(open: Set<Tip>) {
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            finishFirstStart()
            setTipsDone(Tip.entries.toSet() - open)
        }
        AppSettings.replaceInstanceForTest(settings)
        compose.runOnUiThread {
            // As MainActivity: edge to edge, the app handles the keyboard insets itself (no panned window).
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent { key(0) { VocTrainerTheme { VocTrainerApp() } } }
        chooseAvatar()
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) }
        compose.waitUntil(TIMEOUT) { runBlocking { db.vocabularyDao().getAllOfBookIncludingDeleted(ExampleBook.ID).isNotEmpty() } }
    }

    private fun chooseAvatar() {
        compose.waitUntil(TIMEOUT) { textExists(FigureTexts.choiceTitle(false)) }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }
    }

    private fun changeWords(transform: (Vocabulary) -> Vocabulary) = runBlocking {
        val dao = db.vocabularyDao()
        dao.updateAll(dao.getAllOfBookIncludingDeleted(ExampleBook.ID).map(transform))
    }.also {
        // The view models read the words through a flow: let the change arrive before a round starts.
        compose.waitForIdle()
        Thread.sleep(2_000L)
        compose.waitForIdle()
    }

    private fun allWordsAtLevel(level: Int) = changeWords { it.copy(leitnerLevel = level, highestLevel = level) }

    private fun startRound() {
        compose.runOnUiThread { quiz.startRound() }
        compose.waitUntil(TIMEOUT) { asking != null }
    }

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

    private fun waitForTypingKeyboard() {
        compose.waitUntil(TIMEOUT) { asking?.typing != null && exists(QuizTags.INPUT) }
        // The keyboard needs a moment; the tip may come before or after it.
        Thread.sleep(1_000L)
    }

    /** The first finished round of the book: the trigger of "Dein Avatar". */
    private fun firstRound() = runBlocking {
        repository.recordRoundCompleted(AppClock.today().toString(), settings.deviceId, settings.dailyGoal.value)
    }

    // ---- the tips ----

    @Test
    fun n1Selection() {
        launchApp(open = setOf(Tip.SELECTION))
        shotTip("N1_auswahl", "Welche Wörter möchtest du lernen?")
    }

    @Test
    fun n2StartButton() {
        launchApp(open = setOf(Tip.START_BUTTON))
        compose.runOnUiThread { quiz.openSelection() }
        compose.onNodeWithText(SelectionTexts.APPLY).performScrollTo().performClick()
        shotTip("N2_los", "Los geht's")
    }

    @Test
    fun typing() {
        launchApp(open = setOf(Tip.TYPING))
        changeWords { it.copy(leitnerLevel = 3, highestLevel = 3, coreWord = it.coreWord ?: it.english) }
        startRound()
        waitForTypingKeyboard()
        shotTip("Tippen_selbst_tippen", "Selbst tippen")
    }

    @Test
    fun n3RoundResult() {
        launchApp(open = setOf(Tip.ROUND_RESULT))
        startRound()
        answerCorrectUntilFinished()
        shotTip("N3_ergebnis", "Das hast du geschafft")
    }

    @Test
    fun n4AvatarCard() {
        launchApp(open = setOf(Tip.AVATAR_CARD))
        firstRound()
        shotTip("N4_avatar", "Dein Avatar")
    }

    /** "Dein Lernstand", then the chain to "Dein Avatar". */
    @Test
    fun npre4LevelsAndChain() {
        launchApp(open = setOf(Tip.LEVELS, Tip.AVATAR_CARD))
        firstRound()
        shotTip("Npre4_lernstand", "Dein Lernstand")
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        shotTip("Npre4_kette_N4", "Dein Avatar")
    }

    @Test
    fun verbs() {
        launchApp(open = setOf(Tip.VERBS))
        compose.onAllNodes(hasText("Verben") and !hasAnyAncestor(hasTestTag(TipTags.CARD))).onFirst().performClick()
        shotTip("Verben_ueben", "Verben üben")
    }

    /** The first repetition of a typed round: the first word typed wrongly (and copied), the others right. */
    @Test
    fun repeat() {
        launchApp(open = setOf(Tip.REPEAT))
        changeWords { it.copy(leitnerLevel = 3, highestLevel = 3, coreWord = it.coreWord ?: it.english) }
        startRound()
        val end = System.currentTimeMillis() + ROUND_TIMEOUT
        var wrongGiven = false
        while (asking?.question?.isRepeat != true) {
            val state = asking
            val typing = state?.typing
            val core = state?.question?.question?.typing?.coreWord
            if (typing != null && core != null && (typing.phase == TypingPhase.TYPING || typing.phase == TypingPhase.COPYING)) {
                val input = if (typing.phase == TypingPhase.TYPING && !wrongGiven) "qqqqq" else core
                if (typing.phase == TypingPhase.TYPING) wrongGiven = true
                compose.runOnUiThread {
                    quiz.typingInput(input)
                    quiz.submitTyped()
                }
            } else if (state != null && state.typing == null && state.phase == AnswerPhase.ANSWERING) {
                compose.runOnUiThread { quiz.answer(state.correctIndex) }
            }
            assertTrue("no repetition came", System.currentTimeMillis() < end)
            Thread.sleep(150)
        }
        waitForTypingKeyboard()
        shotTip("Wiederholung", "Wiederholung")
    }

    @Test
    fun n5AllKnown() {
        launchApp(open = setOf(Tip.ALL_KNOWN))
        shotTip("N5_alles_wichtige", "Jetzt kennst du alles Wichtige")
    }

    // ---- first start ----

    @Test
    fun welcomeBookChoiceAndExampleBookHint() {
        settings = AppSettings(context, SETTINGS, DEVICE).apply { setAutoSpeak(false) }
        AppSettings.replaceInstanceForTest(settings)
        compose.setContent { key(0) { VocTrainerTheme { VocTrainerApp() } } }
        compose.waitUntil(TIMEOUT) { exists(FirstStartTags.WELCOME) }
        shot("W_willkommen")

        compose.onNodeWithText(FirstStartTexts.START).performClick()
        compose.waitUntil(TIMEOUT) { exists(FirstStartTags.BOOK_CHOICE) }
        shot("B1_buchwahl")

        compose.onNodeWithText(FirstStartTexts.EXAMPLE_BOOK_LINK).performClick()
        compose.waitUntil(TIMEOUT) { exists(FirstStartTags.EXAMPLE_HINT) }
        shot("B2_beispielbuch_hinweis")
    }
}
