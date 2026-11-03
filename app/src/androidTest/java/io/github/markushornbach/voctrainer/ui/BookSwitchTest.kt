package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File

/** "Vokabeln verwalten": book list, the switch question after importing a new book, and the start screen following the active book. */
@RunWith(AndroidJUnit4::class)
class BookSwitchTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val SETTINGS = "test_book_switch_settings"
        const val DEVICE = "test_book_switch_device"
        const val TIMEOUT = 5_000L
        const val SIXTH = "testbuch-6"
        const val SEVENTH = "englisch-klasse-7"
        const val EIGHTH = "englisch-klasse-8"
    }

    private fun importFile(bookId: String, title: String, vararg words: String) = """
        {"format": "voctrainer-import", "version": 2, "bookId": "$bookId", "bookTitle": "$title",
         "entries": [${words.joinToString(",") { """{"english": "$it", "german": ["${it}_de"], "unit": 1, "coreWord": "$it"}""" }}]}
    """.trimIndent()

    /** A file the view model reads like one chosen in the file picker. */
    private fun fileUri(name: String, text: String): Uri =
        Uri.fromFile(File(context.cacheDir, name).apply { writeText(text) })

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = inMemoryDatabase(context)
        repository = VocabularyRepository(db)
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(
            AppSettings(context, SETTINGS, DEVICE).apply {
                setAutoSpeak(false)
                // No one-time notices over the screen.
                markAllTipsDone()
            }
        )
        runBlocking {
            repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog", "cat", "cow", "pig"))
            val dog = db.vocabularyDao().getAllIncludingDeleted().first { it.english == "dog" }
            db.vocabularyDao().update(dog.copy(leitnerLevel = 4))
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

    private fun activeBook() = runBlocking { repository.activeBookNow()!!.id }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun importOfANewBookAsksToSwitchAndTheListSwitchesBooks() {
        compose.setContent { VocTrainerTheme { VocabularyScreen(onOpenEditor = {}) } }
        val viewModel = ViewModelProvider(compose.activity)[VocabularyViewModel::class.java]
        waitForText("Klasse 6")
        compose.onNodeWithText("4 Wörter, 1 gelernt").assertExists()
        compose.onNodeWithTag(BOOK_ROW_TAG + SIXTH).assertIsSelected()

        // "Nein": the new book is stored, but the active book stays.
        viewModel.importVocabulary(fileUri("seventh.json", importFile(SEVENTH, "Klasse 7", "bird", "fish")))
        waitForText("Neues Buch „Klasse 7“ angelegt.", substring = true)
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Neues Buch „Klasse 7“ – jetzt zu diesem Buch wechseln?").assertExists()
        compose.onNodeWithText("Nein").performClick()
        compose.waitForIdle()
        assertEquals(SIXTH, activeBook())
        compose.onNodeWithText("2 Wörter, 0 gelernt").assertExists()
        compose.onNodeWithTag(BOOK_ROW_TAG + SEVENTH).assertIsNotSelected()

        // "Ja": the new book becomes active.
        viewModel.importVocabulary(fileUri("eighth.json", importFile(EIGHTH, "Klasse 8", "sun")))
        waitForText("Neues Buch „Klasse 8“ angelegt.", substring = true)
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { activeBook() == EIGHTH }
        waitForText("Du lernst jetzt mit „Klasse 8“.")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag(BOOK_ROW_TAG + EIGHTH).assertIsSelected()

        // Tapping another book in the list makes it active; nothing else changes.
        compose.onNodeWithTag(BOOK_ROW_TAG + SIXTH).performClick()
        compose.waitUntil(TIMEOUT) { activeBook() == SIXTH }
        compose.onNodeWithTag(BOOK_ROW_TAG + SIXTH).assertIsSelected()
        compose.onNodeWithTag(BOOK_ROW_TAG + EIGHTH).assertIsNotSelected()
        compose.onNodeWithText("4 Vokabeln").assertDoesNotExist()
        assertEquals(7, runBlocking { db.vocabularyDao().getAllBooksIncludingDeleted().size })
    }

    @Test
    fun importIntoAnExistingInactiveBookNamesItAndAsksNothing() {
        runBlocking { repository.importVocabulary(importFile(SEVENTH, "Klasse 7", "bird")) }
        compose.setContent { VocTrainerTheme { VocabularyScreen(onOpenEditor = {}) } }
        val viewModel = ViewModelProvider(compose.activity)[VocabularyViewModel::class.java]

        viewModel.importVocabulary(fileUri("seventh.json", importFile(SEVENTH, "Klasse 7", "bird", "fish")))

        waitForText("Buch „Klasse 7“: 1 Vokabeln importiert", substring = true)
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("jetzt zu diesem Buch wechseln?", substring = true).assertDoesNotExist()
        assertEquals(SIXTH, activeBook())
    }

    @Test
    fun startScreenShowsTheActiveBookAndItsWords() {
        runBlocking { repository.importVocabulary(importFile(SEVENTH, "Klasse 7", "bird", "fish", "ant", "bee", "owl", "elk")) }
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        val quiz = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
        compose.waitUntil(TIMEOUT) { quiz.startState.value.loaded }
        compose.onNodeWithTag(BOOK_TITLE_TAG).assertTextEquals("Klasse 6")
        assertEquals(4, quiz.startState.value.wordCount)

        runBlocking { repository.setActiveBook(SEVENTH) }

        compose.waitUntil(TIMEOUT) { quiz.startState.value.wordCount == 6 }
        compose.onNodeWithTag(BOOK_TITLE_TAG).assertTextEquals("Klasse 7")
        // The avatar of this book still has to be chosen; the progress counts only its words.
        assertEquals(6, quiz.startState.value.progress.total)
    }

    private fun waitForText(text: String, substring: Boolean) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    }
}
