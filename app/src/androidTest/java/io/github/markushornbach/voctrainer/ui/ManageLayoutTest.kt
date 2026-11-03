package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.Book
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inMemoryDatabase
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The three sections of "Vokabeln verwalten": ⓘ buttons, highlighted buttons, the restart button. */
@RunWith(AndroidJUnit4::class)
class ManageLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_manage_layout_settings"
        const val DEVICE = "test_manage_layout_device"
        const val TIMEOUT = 5_000L
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        // No own book yet: only the example book (active).
        db = inMemoryDatabase(context)
        runBlocking {
            val repository = VocabularyRepository(db, ExampleBook.loader(context))
            repository.ensureExampleBook()
            repository.setActiveBook(ExampleBook.ID)
        }
        VocTrainerDatabase.replaceInstanceForTest(db)
        settings = AppSettings(context, SETTINGS, DEVICE)
        AppSettings.replaceInstanceForTest(settings)
        compose.setContent {
            VocTrainerTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    VocabularyScreen(onOpenEditor = {}, modifier = Modifier.padding(padding), snackbarHostState = snackbarHostState)
                }
            }
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

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** A filled button differs from the page background just inside its left edge; an outlined one does not. */
    private fun isFilled(label: String): Boolean {
        val node = compose.onNode(hasText(label) and hasClickAction()).performScrollTo()
        val button = node.fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val y = button.center.y.toInt()
        return pixels[(button.left + 24f).toInt(), y].toArgb() != pixels[2, y].toArgb()
    }

    @Test
    fun theHeadersOpenTheirInfoPages() {
        waitForText(ManageVocabularyTexts.BOOKS_TITLE)
        compose.onNodeWithText(ManageVocabularyTexts.BACKUP_TITLE).assertExists()
        compose.onNodeWithText(RestartBookTexts.SECTION_TITLE).assertExists()

        for (tag in listOf(BOOKS_INFO_TAG, BACKUP_INFO_TAG)) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(hasText(InfoPage.BOOKS.title) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("OK").performClick()
            compose.waitUntil(TIMEOUT) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        }

        compose.onNodeWithTag(RESTART_INFO_TAG, useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(InfoPage.RESTART.title) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theCountLineIsGone() {
        waitForText(ManageVocabularyTexts.IMPORT)
        compose.onAllNodes(hasText(" Vokabeln", substring = true) and !hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun nothingToResetDisablesTheButtonAndShowsTheHint() {
        // The example book has no progress yet.
        waitForText(RestartBookTexts.NOTHING_TO_RESET)
        compose.onNode(hasText(RestartBookTexts.TITLE) and hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun highlightingFollowsTheFirstOwnBook() {
        waitForText(ManageVocabularyTexts.IMPORT)
        assertEquals(true, isFilled(ManageVocabularyTexts.IMPORT))
        assertEquals(false, isFilled(ManageVocabularyTexts.EXPORT))
        assertEquals(false, isFilled(ManageVocabularyTexts.RESTORE))

        runBlocking { db.bookDao().insert(Book("eigenes-buch", "Eigenes Buch", Book.DEFAULT_LANGUAGE, isActive = false)) }
        compose.waitUntil(TIMEOUT) { !isFilled(ManageVocabularyTexts.IMPORT) }
        assertEquals(true, isFilled(ManageVocabularyTexts.EXPORT))
        assertEquals(true, isFilled(ManageVocabularyTexts.RESTORE))
        // "Buch neu beginnen" is never filled.
        assertEquals(false, isFilled(RestartBookTexts.TITLE))
    }
}
