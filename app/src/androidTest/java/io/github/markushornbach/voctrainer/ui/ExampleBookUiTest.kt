package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.EXAMPLE_BOOK_TEST_SUFFIX
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inMemoryDatabase
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A fresh install starts with the example book. The test app (these tests run in it) always lists
 * it in "Vokabeln verwalten" as "Beispielbuch Englisch (Test)"; tapping it makes it active. The
 * help has no row for it.
 */
@RunWith(AndroidJUnit4::class)
class ExampleBookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val SETTINGS = "test_example_book_ui_settings"
        const val DEVICE = "test_example_book_ui_device"
        const val TIMEOUT = 5_000L
        const val OTHER = "testbuch-6"
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = inMemoryDatabase(context)
        repository = VocabularyRepository(db, ExampleBook.loader(context))
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(
            AppSettings(context, SETTINGS, DEVICE).apply {
                setAutoSpeak(false)
                markAllTipsDone()
            }
        )
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

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun freshInstallShowsTheExampleBookOnTheStartScreen() {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        waitForText("Beispielbuch Englisch")
        compose.waitUntil(TIMEOUT) { runBlocking { repository.activeBookNow()?.id } == ExampleBook.ID }
    }

    /** "Klasse 6" (active) and the example book (inactive), as on a device with a real book. */
    private fun withOtherBook() = runBlocking {
        repository.importVocabulary(
            """{"format": "voctrainer-import", "version": 2, "bookId": "$OTHER", "bookTitle": "Klasse 6",
                "entries": [{"english": "dog", "german": ["Hund"], "unit": 1, "coreWord": "dog"}]}"""
        )
        repository.ensureExampleBook()
        assertEquals(OTHER, repository.activeBookNow()!!.id)
    }

    private val listed = "Beispielbuch Englisch" + EXAMPLE_BOOK_TEST_SUFFIX

    @Test
    fun theBookListShowsTheActiveExampleBookWithTheTestSuffix() {
        withOtherBook()
        runBlocking { repository.setActiveBook(ExampleBook.ID) }
        compose.setContent { VocTrainerTheme { VocabularyScreen(onOpenEditor = {}) } }
        waitForText(listed)
        compose.onNodeWithText("150 Wörter, 0 gelernt").assertExists()
        compose.onNodeWithTag(BOOK_ROW_TAG + ExampleBook.ID).assertIsSelected()
        compose.onNodeWithTag(BOOK_ROW_TAG + OTHER).performClick()
        compose.waitUntil(TIMEOUT) { runBlocking { repository.activeBookNow()!!.id } == OTHER }
        compose.onNodeWithTag(BOOK_ROW_TAG + OTHER).assertIsSelected()
        // The test app keeps it listed.
        compose.onNodeWithTag(BOOK_ROW_TAG + ExampleBook.ID).assertExists()
    }

    @Test
    fun theTestAppListsTheInactiveExampleBookAndTappingItMakesItActive() {
        withOtherBook()
        compose.setContent { VocTrainerTheme { VocabularyScreen(onOpenEditor = {}) } }
        // The start of the app applies the example book again; it stays inactive.
        ViewModelProvider(compose.activity)[QuizViewModel::class.java]
        waitForText(listed)
        compose.onNodeWithTag(BOOK_ROW_TAG + OTHER).assertIsSelected()
        compose.onNodeWithTag(BOOK_ROW_TAG + ExampleBook.ID).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { runBlocking { repository.activeBookNow()!!.id } == ExampleBook.ID }
        compose.onNodeWithTag(BOOK_ROW_TAG + ExampleBook.ID).assertIsSelected()
        // Nothing is lost: the other book keeps its word.
        assertEquals(1, runBlocking { db.vocabularyDao().getAllOfBookIncludingDeleted(OTHER).size })
    }

    @Test
    fun theHelpHasNoRowForTheExampleBook() {
        compose.setContent {
            VocTrainerTheme { HelpScreen(viewModel = viewModel(), settings = AppSettings.getInstance(context), onBack = {}) }
        }
        compose.onNodeWithText(TipTexts.SHOW_AGAIN, substring = true).assertExists()
        compose.onNodeWithText(FirstStartTexts.EXAMPLE_BOOK, substring = true).assertDoesNotExist()
    }
}
