package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Wort zum Tippen" in "Vokabel hinzufügen" and on the edit page, and restoring an
 * ignored entry without core word. Runs on an in-memory database with its own settings.
 */
@RunWith(AndroidJUnit4::class)
class CoreWordEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase

    private companion object {
        const val SETTINGS = "test_core_word_settings"
        const val DEVICE = "test_core_word_device"
        const val TIMEOUT = 5_000L
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(AppSettings(context, SETTINGS, DEVICE))
    }

    @After
    fun tearDown() {
        // Stop the ViewModels' database flows before the database is closed.
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        VocTrainerDatabase.replaceInstanceForTest(null)
        AppSettings.replaceInstanceForTest(null)
        db.closeAfterRunningQueries()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    private fun chip(word: String) =
        compose.onNode(hasText(word) and hasAnyAncestor(hasTestTag(CoreWordTags.CHIPS)))

    private fun noChip(word: String) =
        compose.onNode(hasText(word) and hasAnyAncestor(hasTestTag(CoreWordTags.CHIPS))).assertDoesNotExist()

    /** Taps a button; on the long edit page it may first have to be scrolled into view (the keyboard is open). */
    private fun press(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun stored(): List<Vocabulary> = runBlocking { db.vocabularyDao().getAllIncludingDeleted() }

    private fun openHandEntry() {
        compose.setContent { VocTrainerTheme { VocabularyScreen(onOpenEditor = {}) } }
        compose.onNodeWithText(DebugMarker.label("Vokabel hinzufügen")).performClick()
    }

    private fun fillGermanAndUnit() {
        compose.onNodeWithText("Deutsch").performTextInput("jn. pflegen")
        compose.onNodeWithText("Unit").performTextInput("1")
    }

    @Test
    fun handEntryWithSeveralWordsSavesOnlyWithATappedWord() {
        openHandEntry()
        val germanTop = compose.onNodeWithText("Deutsch").getUnclippedBoundsInRoot().top

        compose.onNodeWithText("Englisch").performTextInput("care for sb.")

        // The chips appear in the reserved row, the fields below do not move.
        assertEquals(germanTop, compose.onNodeWithText("Deutsch").getUnclippedBoundsInRoot().top)
        chip("care").assertIsNotSelected()
        chip("for").assertIsNotSelected()
        noChip("sb.")
        noChip("sb")
        fillGermanAndUnit()

        press("Speichern")
        compose.onNodeWithText(CoreWordTexts.CHOOSE).assertExists()
        compose.waitForIdle()
        assertTrue(stored().isEmpty())

        chip("for").performScrollTo().performClick()
        chip("for").assertIsSelected()
        press("Speichern")
        compose.waitUntil(TIMEOUT) { stored().isNotEmpty() }
        val saved = stored().single()
        assertEquals("care for sb.", saved.english)
        assertEquals("for", saved.coreWord)
        assertEquals(false, saved.isDeleted)
    }

    @Test
    fun handEntryWithOneWordTakesItAutomatically() {
        openHandEntry()
        val germanTop = compose.onNodeWithText("Deutsch").getUnclippedBoundsInRoot().top

        compose.onNodeWithText("Englisch").performTextInput("(to) walk")

        assertEquals(germanTop, compose.onNodeWithText("Deutsch").getUnclippedBoundsInRoot().top)
        chip("walk").assertIsSelected()
        noChip("to")
        fillGermanAndUnit()
        press("Speichern")
        compose.waitUntil(TIMEOUT) { stored().isNotEmpty() }
        assertEquals("walk", stored().single().coreWord)
    }

    @Test
    fun handEntryWithoutAnyWordAsksForAnEnglishWord() {
        openHandEntry()
        compose.onNodeWithText("Englisch").performTextInput("…")
        fillGermanAndUnit()
        press("Speichern")
        compose.onNodeWithText(CoreWordTexts.NO_WORD).assertExists()
        compose.waitForIdle()
        assertTrue(stored().isEmpty())
    }

    private fun editorViewModel() = ViewModelProvider(compose.activity)[VocabularyEditorViewModel::class.java]

    @Test
    fun editPageClearsAWordThatIsNoLongerInTheText() {
        val entry = Vocabulary(
            id = "e", english = "care for sb.", germanMeanings = listOf("jn. pflegen"), unit = 1,
            leitnerLevel = 3, highestLevel = 4, coreWord = "for", bookId = TEST_BOOK_ID
        )
        runBlocking { db.vocabularyDao().insertAll(listOf(entry)) }
        val viewModel = editorViewModel()
        viewModel.startEditing(entry)
        compose.setContent { VocTrainerTheme { VocabularyEditScreen(viewModel, onClose = {}) } }

        chip("for").assertIsSelected()
        compose.onNodeWithText("Englisch").performTextReplacement("care about sb.")
        noChip("for")
        chip("care").assertIsNotSelected()
        chip("about").assertIsNotSelected()

        press("Speichern")
        compose.onNodeWithText(CoreWordTexts.CHOOSE).assertExists()
        compose.waitForIdle()
        assertEquals(entry, stored().single())

        chip("about").performScrollTo().performClick()
        press("Speichern")
        compose.waitUntil(TIMEOUT) { stored().single().coreWord == "about" }
        val saved = stored().single()
        assertEquals("care about sb.", saved.english)
        assertEquals(3, saved.leitnerLevel)
        assertEquals(4, saved.highestLevel)
    }

    @Test
    fun restoringAnIgnoredEntryWithoutCoreWordNeedsAChosenWord() {
        val ignored = Vocabulary(
            id = "i", english = "care for sb.", germanMeanings = listOf("jn. pflegen"), unit = 1,
            leitnerLevel = 4, highestLevel = 4, isDeleted = true, updatedAt = 1_000L, bookId = TEST_BOOK_ID
        )
        runBlocking { db.vocabularyDao().insertAll(listOf(ignored)) }
        val viewModel = editorViewModel()
        viewModel.setShowIgnored(true)
        var editing by mutableStateOf(false)
        compose.setContent {
            VocTrainerTheme {
                if (editing) {
                    VocabularyEditScreen(viewModel, onClose = { editing = false })
                } else {
                    VocabularyListScreen(viewModel, onBack = {}, onEdit = {
                        viewModel.startEditing(it)
                        editing = true
                    })
                }
            }
        }

        press("Wiederherstellen")
        // "Wiederherstellen" opened the edit page instead of restoring at once.
        compose.onNodeWithText(RESTORE_WITH_CORE_WORD_HINT).assertExists()
        compose.waitForIdle()
        assertEquals(ignored, stored().single())

        press("Wiederherstellen")
        compose.onNodeWithText(CoreWordTexts.CHOOSE).assertExists()
        compose.waitForIdle()
        assertTrue(stored().single().isDeleted)

        chip("care").performScrollTo().performClick()
        press("Wiederherstellen")
        compose.waitUntil(TIMEOUT) { !stored().single().isDeleted }
        val restored = stored().single()
        assertEquals("care", restored.coreWord)
        assertEquals(4, restored.leitnerLevel)
        assertTrue(restored.updatedAt > ignored.updatedAt)
        compose.waitUntil(TIMEOUT) { !editing }
    }

    @Test
    fun restoringAnIgnoredEntryWithCoreWordWorksAtOnce() {
        val ignored = Vocabulary(
            id = "i", english = "bus", germanMeanings = listOf("Bus"), unit = 1, leitnerLevel = 2,
            isDeleted = true, coreWord = "bus", updatedAt = 1_000L, bookId = TEST_BOOK_ID
        )
        runBlocking { db.vocabularyDao().insertAll(listOf(ignored)) }
        val viewModel = editorViewModel()
        viewModel.setShowIgnored(true)
        compose.setContent { VocTrainerTheme { VocabularyListScreen(viewModel, onBack = {}, onEdit = {}) } }

        press("Wiederherstellen")
        compose.waitUntil(TIMEOUT) { !stored().single().isDeleted }
        assertEquals(2, stored().single().leitnerLevel)
    }
}
