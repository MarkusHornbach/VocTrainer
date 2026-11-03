package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.markushornbach.voctrainer.MainActivity
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min

/**
 * The real [MainActivity] looks the same with every system font size and display size (fixed UI scale):
 * each screen is captured with the system defaults, then the system setting is changed while the screen is
 * open (the activity is recreated, the round and the screen stay) and the capture is compared pixel by pixel.
 * The app window is captured without the keyboard and the system bars; the content is aligned below the
 * status bar. The system bars and the keyboard follow the system display size, so the free height can differ
 * by a few pixels (see [difference]); a typing question is compared only above the keyboard.
 *
 * Emulator only: it changes the system settings `font_scale` and `wm density` and always resets them.
 * In-memory database and own settings, the installed app's data is never touched. All captures are saved
 * in `files/fixed_ui_scale` of the app's external storage, the result per screen is logged with [TAG].
 */
@RunWith(AndroidJUnit4::class)
class FixedUiScaleScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val dir = File(context.getExternalFilesDir(null), "fixed_ui_scale")
    private val failures = mutableListOf<String>()

    private companion object {
        const val TAG = "FixedUiScaleTest"
        const val SETTINGS = "test_fixed_ui_scale_settings"
        const val DEVICE = "test_fixed_ui_scale_device"
        const val TIMEOUT = 20_000L
        const val ROUND_TIMEOUT = 60_000L

        /** Further captures of the typing question, [BLINK_STEP_MS] apart (the cursor blinks every 500 ms). */
        const val BLINK_TRIES = 4
        const val BLINK_STEP_MS = 250L

        /** See [difference]. */
        const val MAX_SHIFTED_SHARE = 0.25

        /** Animations (ripples, the keyboard) and the speech engine settle before a capture. */
        const val SETTLE_MS = 1_500L
    }

    /** A system setting, applied with a shell command; [DEFAULT] = font size and display size on their default step. */
    private enum class SystemSetting(val label: String) {
        DEFAULT("default"),
        FONT_085("font0.85"),
        FONT_130("font1.3"),
        FONT_200("font2.0"),
        DENSITY_SMALLEST("densitySmallest"),
        DENSITY_LARGEST("densityLargest")
    }

    // Unit 1 is the Auswahl: "garden" is asked first (it can become "gelernt" today) and typed, then "bread" with
    // suggestions. Unit 2 only gives the suggestions.
    private val words = listOf(
        Vocabulary(english = "garden", germanMeanings = listOf("Garten", "Gartenanlage"), unit = 1, leitnerLevel = 3, coreWord = "garden"),
        Vocabulary(english = "bread", germanMeanings = listOf("Brot"), unit = 1, leitnerLevel = 1),
        Vocabulary(english = "cheese", germanMeanings = listOf("Käse"), unit = 2, leitnerLevel = 0),
        Vocabulary(english = "milk", germanMeanings = listOf("Milch"), unit = 2, leitnerLevel = 0),
        Vocabulary(english = "apple", germanMeanings = listOf("Apfel"), unit = 2, leitnerLevel = 0),
        Vocabulary(english = "river", germanMeanings = listOf("Fluss"), unit = 2, leitnerLevel = 0)
    )

    private val quiz: QuizViewModel get() = ViewModelProvider(activity())[QuizViewModel::class.java]

    @Before
    fun setUp() {
        assumeTrue("changes system settings: emulator only", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        resetSystemSettings()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            markAllTipsDone()
            notificationPermissionAsked = true
        }
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            db.vocabularyDao().insertAll(words.inTestBook())
            VocabularyRepository(db).saveQuizFilter(QuizMode.VOCABULARY, QuizFilter(unit = UnitChoice.Number(1)))
            val now = System.currentTimeMillis()
            db.figureDao().upsert(
                Figure(slot = 0, kind = FigureKind.entries.first().key, pointsAtStart = 0, stage = 0, startedAt = now, updatedAt = now, bookId = TEST_BOOK_ID)
            )
        }
    }

    @After
    fun tearDown() {
        resetSystemSettings()
        if (::scenario.isInitialized) scenario.close()
        if (::db.isInitialized) {
            VocTrainerDatabase.replaceInstanceForTest(null)
            AppSettings.replaceInstanceForTest(null)
            db.closeAfterRunningQueries()
            context.deleteSharedPreferences(SETTINGS)
            context.deleteSharedPreferences(DEVICE)
        }
    }

    // ---- system settings ----

    private fun resetSystemSettings() {
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("wm density reset")
    }

    /** The smallest and largest step of "Anzeigegröße", computed as the system settings app does. */
    private fun densityStep(largest: Boolean): Int {
        val stable = DisplayMetrics.DENSITY_DEVICE_STABLE
        if (!largest) return (stable * 0.85f).toInt()
        val size = device.executeShellCommand("wm size").substringAfter("Physical size:").trim().substringBefore('\n')
        val minPx = size.split('x').minOf { it.trim().toInt() }
        val maxDensity = DisplayMetrics.DENSITY_MEDIUM * minPx / 320
        return min(maxDensity, (stable * 1.5f).toInt())
    }

    private fun command(setting: SystemSetting): String = when (setting) {
        SystemSetting.DEFAULT -> "settings put system font_scale 1.0; wm density reset"
        SystemSetting.FONT_085 -> "settings put system font_scale 0.85"
        SystemSetting.FONT_130 -> "settings put system font_scale 1.3"
        SystemSetting.FONT_200 -> "settings put system font_scale 2.0"
        SystemSetting.DENSITY_SMALLEST -> "wm density ${densityStep(largest = false)}"
        SystemSetting.DENSITY_LARGEST -> "wm density ${densityStep(largest = true)}"
    }

    /** Applies [setting] and waits until the activity has been recreated with it. */
    private fun apply(setting: SystemSetting) {
        val before = activity()
        command(setting).split(';').forEach { device.executeShellCommand(it.trim()) }
        val end = System.currentTimeMillis() + TIMEOUT
        while (activity() === before) {
            assertTrue("activity was not recreated for ${setting.label}", System.currentTimeMillis() < end)
            Thread.sleep(100)
        }
        compose.waitForIdle()
    }

    // ---- app ----

    private fun activity(): MainActivity {
        var current: MainActivity? = null
        scenario.onActivity { current = it }
        return current!!
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(TIMEOUT) {
            quiz.figureState.value.current != null && quiz.startState.value.loaded && quiz.streak.value != null
        }
        // The speaker button appears once the speech engine is ready; wait for it, so it is in every capture.
        val end = System.currentTimeMillis() + 10_000
        while (!quiz.speechAvailable.value && System.currentTimeMillis() < end) Thread.sleep(100)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun dialogOpen() = compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()

    private fun back() {
        device.pressBack()
        compose.waitForIdle()
    }

    /** Status bar top, navigation bar bottom and keyboard bottom insets of the current window in px. */
    private fun insets(): Triple<Int, Int, Int> {
        var result = Triple(0, 0, 0)
        scenario.onActivity {
            val insets = ViewCompat.getRootWindowInsets(it.window.decorView)
            result = Triple(
                insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0,
                insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0,
                insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            )
        }
        return result
    }

    private fun waitForKeyboard() {
        val end = System.currentTimeMillis() + 5_000
        var last = -1
        var stableSince = System.currentTimeMillis()
        while (System.currentTimeMillis() < end) {
            val ime = insets().third
            if (ime != last) {
                last = ime
                stableSince = System.currentTimeMillis()
            } else if (ime > 0 && System.currentTimeMillis() - stableSince > 600) {
                return
            }
            Thread.sleep(50)
        }
        failures += "keyboard did not open (ime=$last)"
    }

    private fun keyboard(name: String) = name.startsWith("typing")

    private class Capture(val bitmap: Bitmap, val top: Int, val bottom: Int)

    /** The app window (or the open dialog), without keyboard and system bars. */
    private fun capture(name: String, dialog: Boolean, settle: Boolean = true): Capture {
        compose.waitForIdle()
        if (settle) {
            Thread.sleep(SETTLE_MS)
            compose.waitForIdle()
        }
        val node = if (dialog) compose.onAllNodes(isDialog()).onFirst() else compose.onRoot()
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (keyboard(name)) {
            // For a look at the keyboard height: the whole screen with keyboard and system bars.
            val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(dir, "${name}_screen.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val (status, navigation, ime) = insets()
        // A dialog window lies between the system bars; it is compared as a whole.
        return if (dialog) Capture(bitmap, 0, bitmap.height) else Capture(bitmap, status, bitmap.height - maxOf(navigation, ime))
    }

    private class Difference(val text: String, val failed: Boolean)

    /**
     * Compares the content of [actual] with [expected] below the status bar; null if identical. With the same
     * free height every row must be identical. With other system bars the free height differs by a few pixels
     * and what sits at the bottom or is centred in the free space moves by up to that difference (centred
     * elements by half a difference, so their anti-aliasing changes, too): then a row also counts as the same
     * if it matches at any offset up to the difference, and only more than [MAX_SHIFTED_SHARE] other rows fail.
     * A scaled screen differs in most rows (font size 1.3 before the fixed UI scale: 85 %).
     */
    private fun difference(expected: Capture, actual: Capture): Difference? {
        if (expected.bitmap.width != actual.bitmap.width) {
            return Difference("width ${expected.bitmap.width} → ${actual.bitmap.width}", failed = true)
        }
        val width = expected.bitmap.width
        val expectedHeight = expected.bottom - expected.top
        val actualHeight = actual.bottom - actual.top
        val shift = actualHeight - expectedHeight
        val a = IntArray(width)
        val b = IntArray(width)
        fun same(y: Int, offset: Int): Boolean {
            val other = y + offset
            if (other !in 0 until actualHeight) return false
            actual.bitmap.getPixels(b, 0, width, 0, actual.top + other, width, 1)
            return a.contentEquals(b)
        }
        var firstRow = -1
        var rows = 0
        for (y in 0 until expectedHeight) {
            expected.bitmap.getPixels(a, 0, width, 0, expected.top + y, width, 1)
            if (same(y, 0) || (shift != 0 && (minOf(0, shift)..maxOf(0, shift)).any { same(y, it) })) continue
            if (firstRow < 0) firstRow = y
            rows++
        }
        if (rows == 0) return null
        val text = "$rows of $expectedHeight rows differ, first at $firstRow (content height $expectedHeight → $actualHeight)"
        return Difference(text, failed = shift == 0 || rows > expectedHeight * MAX_SHIFTED_SHARE)
    }

    /**
     * Captures the current screen with the system defaults and with every other [SystemSetting] and compares
     * them. [ensure] brings the screen back after a recreation where needed (a dialog, the keyboard).
     */
    private fun checkScreen(name: String, dialog: Boolean = false, keyboard: Boolean = false, ensure: () -> Unit = {}) {
        // An activity recreated by a changed system setting keeps the cursor in the input field but not its focus
        // outline (also after a rotation), so with the keyboard the reference is taken after such a change, too.
        if (keyboard) {
            apply(SystemSetting.FONT_085)
            apply(SystemSetting.DEFAULT)
        }
        ensure()
        if (keyboard) waitForKeyboard()
        val expected = capture("${name}_${SystemSetting.DEFAULT.label}", dialog)
        val results = mutableListOf<String>()
        for (setting in SystemSetting.entries - SystemSetting.DEFAULT) {
            try {
                apply(setting)
                ensure()
                if (keyboard) waitForKeyboard()
                var difference = difference(expected, capture("${name}_${setting.label}", dialog))
                // The cursor blinks: another capture a little later shows it in the same phase as the reference.
                repeat(if (keyboard) BLINK_TRIES else 0) {
                    if (difference == null) return@repeat
                    Thread.sleep(BLINK_STEP_MS)
                    difference = difference(expected, capture("${name}_${setting.label}", dialog, settle = false))
                }
                results += "${setting.label}: ${difference?.text ?: "identical"}"
                if (difference?.failed == true) failures += "$name ${setting.label}: ${difference.text}"
            } finally {
                apply(SystemSetting.DEFAULT)
            }
        }
        ensure()
        Log.i(TAG, "$name: ${results.joinToString("; ")}")
    }

    @Test
    fun theSystemFontAndDisplaySizeDoNotReachTheApp() {
        device.executeShellCommand(command(SystemSetting.FONT_200))
        device.executeShellCommand(command(SystemSetting.DENSITY_LARGEST))
        launch()
        val stable = DisplayMetrics.DENSITY_DEVICE_STABLE
        val system = context.applicationContext.resources.configuration
        assertEquals(2f, system.fontScale, 0.001f)
        assertEquals(densityStep(largest = true), system.densityDpi)
        val app = activity().resources.configuration
        assertEquals(FixedUiScale.FONT_SCALE, app.fontScale, 0.001f)
        assertEquals(stable, app.densityDpi)
        assertEquals(stable, activity().resources.displayMetrics.densityDpi)
        // Compose uses the same density: the streak bar is 64 dp high at the default density.
        val bar = compose.onNodeWithTag(STREAK_BAR_TAG).fetchSemanticsNode().boundsInWindow
        assertEquals(64f * stable / DisplayMetrics.DENSITY_DEFAULT, bar.height, 0.5f)

        // The test tools show both.
        compose.onAllNodes(hasContentDescription("Einstellungen")).onFirst().performClick()
        compose.onNodeWithText(DebugMarker.label("Testwerkzeuge")).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { exists(TEST_DISPLAY_INFO_TAG) }
        compose.onNodeWithTag(TEST_DISPLAY_INFO_TAG).assertTextEquals(
            FixedUiScale.testToolsLine(2f, densityStep(largest = true), stable, 1f, stable)
        )
    }

    @Test
    fun everyScreenLooksTheSameWithEverySystemFontAndDisplaySize() {
        dir.deleteRecursively()
        dir.mkdirs()
        launch()
        checkScreen("start")

        checkScreen("infoPage", dialog = true) {
            if (!dialogOpen()) compose.onNodeWithTag(STREAK_INFO_TAG, useUnmergedTree = true).performClick()
            compose.waitUntil(TIMEOUT) { dialogOpen() }
        }
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(TIMEOUT) { !dialogOpen() }

        compose.onNode(SemanticsMatcher("opens the selection") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Auswahl ändern" }).performClick()
        compose.waitUntil(TIMEOUT) { quiz.filtersExpanded.value }
        checkScreen("selection")
        back()
        compose.waitUntil(TIMEOUT) { !quiz.filtersExpanded.value }

        compose.onNodeWithTag(STAGE_BAR_TAG, useUnmergedTree = true).performClick()
        compose.waitUntil(TIMEOUT) { textExists(BadgeTexts.TITLE) }
        checkScreen("avatar")
        back()

        compose.onAllNodes(hasContentDescription("Einstellungen")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { textExists("Vokabeln verwalten") }
        checkScreen("settings")
        compose.onNodeWithText("Vokabeln verwalten").performClick()
        compose.waitUntil(TIMEOUT) { textExists(ManageVocabularyTexts.EXPORT) }
        checkScreen("manage")
        back()
        back()

        compose.onNodeWithText("Los geht's").performClick()
        compose.waitUntil(TIMEOUT) { (quiz.round.value as? QuizRoundState.Asking)?.typing != null }
        compose.onNodeWithTag(QuizTags.INPUT).performTextReplacement("xyzxyz")
        compose.onNodeWithTag(QuizTags.CHECK).performClick()
        compose.waitUntil(TIMEOUT) { exists(QuizTags.FEEDBACK) }
        checkScreen("typingWrong", keyboard = true) {
            compose.waitUntil(TIMEOUT) { exists(QuizTags.INPUT) }
        }
        val typing = quiz.round.value as QuizRoundState.Asking
        compose.onNodeWithTag(QuizTags.INPUT).performTextReplacement("garden")
        compose.onNodeWithTag(QuizTags.CHECK).performClick()
        compose.waitUntil(TIMEOUT) {
            val now = quiz.round.value
            now is QuizRoundState.Asking && now.question !== typing.question && now.typing == null
        }
        checkScreen("suggestions")

        // Answer the rest correctly ("garden" comes back as a repetition at the end).
        var answered: Any? = null
        compose.waitUntil(ROUND_TIMEOUT) {
            val state = quiz.round.value as? QuizRoundState.Asking
            if (state != null && state.question !== answered && state.phase == AnswerPhase.ANSWERING) {
                answered = state.question
                if (state.typing != null) {
                    compose.onNodeWithTag(QuizTags.INPUT).performTextReplacement("garden")
                    compose.onNodeWithTag(QuizTags.CHECK).performClick()
                } else {
                    compose.onNode(hasText(state.options[state.correctIndex]) and hasClickAction()).performClick()
                }
            }
            (quiz.round.value as? QuizRoundState.Finished)?.details != null
        }
        checkScreen("result")

        failures.forEach { Log.e(TAG, "FAIL $it") }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
