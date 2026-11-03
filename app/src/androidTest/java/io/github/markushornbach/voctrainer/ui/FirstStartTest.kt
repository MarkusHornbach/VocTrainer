package io.github.markushornbach.voctrainer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inMemoryDatabase
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
import java.io.File

/** The first start (welcome, book choice, example-book hint, avatar choice) and the tips N1, N2. */
@RunWith(AndroidJUnit4::class)
class FirstStartTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    /** Bumped to "restart" the app: new settings from the same files, new view models, new composition. */
    private var generation by mutableIntStateOf(0)

    /** What the stubbed file picker returns; null = cancelled. */
    private var pickedFile: Uri? = null

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?
            ) {
                val uri = pickedFile
                if (uri == null) dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
                else dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
            }
        }
    }

    private companion object {
        const val SETTINGS = "test_first_start_settings"
        const val DEVICE = "test_first_start_device"
        const val TIMEOUT = 10_000L
        const val NO_TIP_WAIT = 2_000L
        const val OWN_BOOK = "englisch-klasse-5"
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
        listOf(SETTINGS, DEVICE).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
    }

    /** A fresh installation unless the preferences were filled before. */
    private fun openSettings() {
        AppSettings.replaceInstanceForTest(AppSettings(context, SETTINGS, DEVICE).apply { setAutoSpeak(false) })
    }

    private fun launch() {
        compose.setContent {
            key(generation) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    VocTrainerTheme { VocTrainerApp() }
                }
            }
        }
    }

    private fun restart() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        openSettings()
        compose.runOnUiThread { generation++ }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTag(tag: String) = compose.waitUntil(TIMEOUT) { exists(tag) }

    private fun waitForText(text: String, substring: Boolean = false) = compose.waitUntil(TIMEOUT) { textExists(text, substring) }

    private fun assertNoTipFor(millis: Long = NO_TIP_WAIT) {
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            compose.waitForIdle()
            assertFalse("no tip expected", exists(TipTags.CARD))
            Thread.sleep(100)
        }
    }

    private fun tipCardShows(text: String) = compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(TipTags.CARD)))
        .fetchSemanticsNodes().isNotEmpty()

    /** Everything below [tag] fits on the screen: no vertical scrolling. */
    private fun assertFitsWithoutScrolling(tag: String) {
        compose.waitForIdle()
        val scrollers = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertTrue("$tag: scroll container expected", scrollers.isNotEmpty())
        for (node in scrollers) {
            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals("$tag must fit without scrolling", 0f, range.maxValue(), 0.5f)
        }
    }

    private fun chooseAvatar() {
        compose.waitUntil(TIMEOUT) { textExists(FigureTexts.choiceTitle(false)) }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
    }

    private fun activeBook() = runBlocking { repository.activeBookNow()?.id }

    private fun fileUri(name: String, text: String): Uri = Uri.fromFile(File(context.cacheDir, name).apply { writeText(text) })

    private val ownBookFile = """
        {"format": "voctrainer-import", "version": 2, "bookId": "$OWN_BOOK", "bookTitle": "Klasse 5",
         "entries": [${listOf("dog", "cat", "cow", "pig", "hen").joinToString(",") { """{"english": "$it", "german": ["${it}_de"], "unit": 1, "coreWord": "$it"}""" }}]}
    """.trimIndent()

    /** "Mein Buch laden" first, the example book as a text link below it. */
    private fun assertBookChoiceOrder() {
        val own = compose.onNodeWithText(FirstStartTexts.OWN_BOOK).getUnclippedBoundsInRoot()
        val example = compose.onNodeWithText(FirstStartTexts.EXAMPLE_BOOK_LINK).getUnclippedBoundsInRoot()
        assertTrue(own.bottom <= example.top)
        compose.onAllNodesWithText("Später änderbar", substring = true).assertCountEquals(0)
    }

    /** The example book, then its hint ("OK"), then the avatar choice. */
    private fun chooseExampleBook() {
        compose.onNodeWithText(FirstStartTexts.EXAMPLE_BOOK_LINK).performClick()
        waitForTag(FirstStartTags.EXAMPLE_HINT)
        assertTrue(textExists(FirstStartTexts.EXAMPLE_HINT))
        compose.onNodeWithText(FirstStartTexts.EXAMPLE_HINT_OK).performClick()
    }

    @Test
    fun freshInstallationGoesStraightToTheBookChoiceAndGetsTheSelectionTip() {
        openSettings()
        launch()
        waitForTag(FirstStartTags.WELCOME)
        assertTrue(textExists(DebugMarker.label(FirstStartTexts.welcomeTitle(true))))
        assertTrue(textExists(FirstStartTexts.WELCOME_LEARN) && textExists(FirstStartTexts.WELCOME_AVATAR))
        // No help or settings during the first start.
        assertTrue(compose.onAllNodes(hasContentDescription("Hilfe")).fetchSemanticsNodes().isEmpty())
        assertFitsWithoutScrolling(FirstStartTags.WELCOME)

        // No tour: the welcome leads straight to the book choice.
        compose.onNodeWithText(FirstStartTexts.START).performClick()
        waitForTag(FirstStartTags.BOOK_CHOICE)
        assertFitsWithoutScrolling(FirstStartTags.BOOK_CHOICE)
        assertBookChoiceOrder()
        chooseExampleBook()

        compose.waitUntil(TIMEOUT) { textExists(FigureTexts.choiceTitle(false)) }
        for (line in FigureTexts.CHOICE_LINES) assertTrue(line, textExists(line))
        assertFalse(settings.firstStartDone.value)
        chooseAvatar()

        compose.waitUntil(TIMEOUT) { settings.firstStartDone.value }
        assertEquals(ExampleBook.ID, activeBook())
        compose.waitUntil(TIMEOUT) { exists(TipTags.CARD) && tipCardShows("Welche Wörter möchtest du lernen?") }
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertTrue(Tip.SELECTION in settings.tips.value.done)
        // The other tips are still open.
        assertFalse(Tip.START_BUTTON in settings.tips.value.done)
    }

    @Test
    fun withoutTipsNoTipComesAndTheHintShowsOnce() {
        openSettings()
        launch()
        waitForTag(FirstStartTags.WELCOME)
        compose.onNodeWithText(FirstStartTexts.WITHOUT_TIPS).performClick()
        waitForTag(FirstStartTags.BOOK_CHOICE)
        chooseExampleBook()
        chooseAvatar()

        waitForText(FirstStartTexts.TIPS_OFF_HINT)
        // All nine tips are done.
        assertEquals(TipState(done = Tip.entries.toSet()), settings.tips.value)
        assertEquals(9, settings.tips.value.done.size)
        assertNoTipFor()

        restart()
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) }
        assertNoTipFor()
        assertFalse(textExists(FirstStartTexts.TIPS_OFF_HINT))
    }

    @Test
    fun ownBookIsLoadedAndActiveWithoutAskingAndAnInvalidFileStaysOnTheChoice() {
        openSettings()
        launch()
        waitForTag(FirstStartTags.WELCOME)
        compose.onNodeWithText(FirstStartTexts.WITHOUT_TIPS).performClick()
        waitForTag(FirstStartTags.BOOK_CHOICE)
        compose.waitUntil(TIMEOUT) { activeBook() == ExampleBook.ID }

        // Cancelled picker: nothing happens.
        pickedFile = null
        compose.onNodeWithText(FirstStartTexts.OWN_BOOK).performClick()
        compose.waitForIdle()
        assertTrue(exists(FirstStartTags.BOOK_CHOICE))

        // A file that is no import file.
        pickedFile = fileUri("invalid.json", """{"format": "something-else", "version": 1}""")
        compose.onNodeWithText(FirstStartTexts.OWN_BOOK).performClick()
        waitForText(FirstStartTexts.FILE_INVALID)
        assertTrue(exists(FirstStartTags.BOOK_CHOICE))
        assertEquals(ExampleBook.ID, activeBook())

        pickedFile = fileUri("own.json", ownBookFile)
        compose.onNodeWithText(FirstStartTexts.OWN_BOOK).performClick()
        compose.waitUntil(TIMEOUT) { textExists(FigureTexts.choiceTitle(false)) }
        // An own book: no hint about the example book.
        assertFalse(exists(FirstStartTags.EXAMPLE_HINT))
        assertFalse(textExists(FirstStartTexts.EXAMPLE_HINT))
        assertEquals(OWN_BOOK, activeBook())
        assertFalse(textExists("jetzt zu diesem Buch wechseln", substring = true))
        assertFalse(textExists("Vokabeln importiert", substring = true))
        chooseAvatar()
        compose.waitUntil(TIMEOUT) { settings.firstStartDone.value }
        compose.waitUntil(TIMEOUT) { textExists("Klasse 5") }
        assertNoTipFor()
    }

    @Test
    fun backGoesThroughTheFlowAsPlanned() {
        openSettings()
        launch()
        waitForTag(FirstStartTags.WELCOME)
        compose.onNodeWithText(FirstStartTexts.START).performClick()
        waitForTag(FirstStartTags.BOOK_CHOICE)
        back()
        waitForTag(FirstStartTags.WELCOME)

        // From the hint back to the book choice, from there back to the welcome.
        compose.onNodeWithText(FirstStartTexts.WITHOUT_TIPS).performClick()
        waitForTag(FirstStartTags.BOOK_CHOICE)
        compose.onNodeWithText(FirstStartTexts.EXAMPLE_BOOK_LINK).performClick()
        waitForTag(FirstStartTags.EXAMPLE_HINT)
        back()
        waitForTag(FirstStartTags.BOOK_CHOICE)
        back()
        waitForTag(FirstStartTags.WELCOME)
        // Leaving the flow halfway changes nothing: it is still open, and no tip was marked.
        assertFalse(settings.firstStartDone.value)
        assertEquals(TipState(), settings.tips.value)
    }

    /** N1 comes once only: a new book later brings no tip. */
    @Test
    fun aNewBookBringsNoSelectionTip() {
        openSettings()
        settings.markAllTipsDone()
        launch()
        chooseAvatar()
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) }
        assertNoTipFor(1_000L)

        runBlocking {
            repository.importVocabulary(ownBookFile)
            repository.setActiveBook(OWN_BOOK)
        }
        chooseAvatar()
        compose.waitUntil(TIMEOUT) { exists(STAGE_BAR_TAG) && textExists("Klasse 5") }
        assertNoTipFor()
    }

    @Test
    fun theHelpPageHasNoTour() {
        openSettings()
        settings.markAllTipsDone()
        launch()
        chooseAvatar()
        compose.onAllNodes(hasContentDescription("Hilfe")).onFirst().performClick()
        waitForText(TipTexts.SHOW_AGAIN, substring = true)
        assertFalse(textExists("Rundgang", substring = true))
        assertTrue(settings.firstStartDone.value)
    }

    @Test
    fun startButtonTipComesOnTheNextVisitAfterTheSelectionTip() {
        openSettings()
        settings.markAllTipsDone()
        settings.resetTips()
        settings.setTipsDone(Tip.entries.toSet() - Tip.SELECTION - Tip.START_BUTTON)
        launch()
        chooseAvatar()
        compose.waitUntil(TIMEOUT) { exists(TipTags.CARD) && tipCardShows("Welche Wörter möchtest du lernen?") }
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        // Same visit: no second tip.
        assertNoTipFor(1_000L)

        // Opening and closing the selection screen (no changes) is the next visit.
        compose.runOnUiThread { quiz.openSelection() }
        compose.onNodeWithText(SelectionTexts.BACK).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { exists(TipTags.CARD) && tipCardShows("Los geht's") }
        compose.onNodeWithText(TipTexts.UNDERSTOOD).performClick()
        compose.waitUntil(TIMEOUT) { !exists(TipTags.CARD) }
        assertTrue(Tip.START_BUTTON in settings.tips.value.done)
    }
}
