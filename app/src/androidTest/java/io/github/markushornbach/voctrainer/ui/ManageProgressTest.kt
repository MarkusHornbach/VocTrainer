package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.PointsDay
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocTrainerJson
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.learning.FigureKind
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
import java.io.File

/** "Lernstand zurücksetzen" / "Buch neu beginnen" as the last section of "Vokabeln verwalten"; the settings have no page "Lernfortschritt verwalten". */
@RunWith(AndroidJUnit4::class)
class ManageProgressTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_manage_progress_settings"
        const val DEVICE = "test_manage_progress_device"
        const val TIMEOUT = 5_000L
        const val TITLE = "Testbuch"
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        VocTrainerDatabase.replaceInstanceForTest(db)
        settings = AppSettings(context, SETTINGS, DEVICE)
        AppSettings.replaceInstanceForTest(settings)
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

    private val repository get() = VocabularyRepository(db)

    private fun points(): Int = runBlocking { repository.totalPointsNow() }

    private fun slotStart(): Int = runBlocking { repository.activeBookNow()!!.figureSlotStart }

    /** A growing avatar and 120 points: something to reset. */
    private fun withProgress() = runBlocking {
        repository.chooseFigure(FigureKind.DRAGON)
        db.pointsDayDao().upsert(PointsDay("2026-10-09", DEVICE, risePoints = 120, updatedAt = 5L, bookId = TEST_BOOK_ID))
    }

    /** "Vokabeln verwalten" with its snackbar, as in the app. */
    private fun showScreen() {
        compose.setContent {
            VocTrainerTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    VocabularyScreen(onOpenEditor = {}, modifier = Modifier.padding(padding), snackbarHostState = snackbarHostState)
                }
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Waits until the state is loaded and "Buch neu beginnen" can be tapped. */
    private fun waitForEnabledButton() {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(RestartBookTexts.TITLE) and hasClickAction() and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theSettingsHaveNoPageLernfortschrittVerwalten() {
        compose.setContent {
            VocTrainerTheme {
                SettingsScreen(
                    settings = settings, onBack = {}, onOpenManageVocabulary = {},
                    onOpenAppearance = {}, onOpenPractice = {}, onOpenLearning = {}, onOpenTestTools = {}
                )
            }
        }
        compose.onNodeWithText("Vokabeln verwalten").assertExists()
        compose.onNodeWithText("Lernfortschritt verwalten").assertDoesNotExist()
        // The subtitle of "Vokabeln verwalten" names the action that moved there.
        compose.onNodeWithText(RestartBookTexts.TITLE, substring = true).assertExists()
    }

    @Test
    fun theRestartSectionIsTheLastOneOfVokabelnVerwalten() {
        withProgress()
        showScreen()
        waitForEnabledButton()
        val section = compose.onNodeWithText(RestartBookTexts.SECTION_TITLE).getUnclippedBoundsInRoot()
        for (above in listOf(ManageVocabularyTexts.IMPORT, ManageVocabularyTexts.EXPORT, ManageVocabularyTexts.RESTORE, BookTexts.LIST_TITLE)) {
            assertTrue(above, compose.onNodeWithText(above).getUnclippedBoundsInRoot().bottom <= section.top)
        }
        // The ⓘ of the section opens "Buch neu beginnen" (I11).
        compose.onNodeWithTag(RESTART_INFO_TAG).performScrollTo().performClick()
        waitForText(InfoTexts.RESTART_RESET_TITLE)
    }

    @Test
    fun withoutProgressTheButtonIsDisabledAndTheOldActionsAreGone() {
        showScreen()
        waitForText(RestartBookTexts.NOTHING_TO_RESET)
        compose.onNode(hasText(RestartBookTexts.TITLE) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Lernfortschritt für aktuelle Auswahl zurücksetzen").assertDoesNotExist()
        compose.onNodeWithText("Avatar neu starten").assertDoesNotExist()
    }

    /** Test app path: dialog 1 → "Ohne Sicherung (Test-App)" → dialog 2; every "Abbrechen" resets nothing. */
    @Test
    fun restartWithoutBackupInTheTestAppAndCancelPathsResetNothing() {
        withProgress()
        showScreen()
        waitForEnabledButton()
        compose.onNodeWithText("Lernfortschritt für aktuelle Auswahl zurücksetzen").assertDoesNotExist()
        compose.onNodeWithText("Avatar neu starten").assertDoesNotExist()

        // Dialog 1, cancelled.
        compose.onNodeWithText(RestartBookTexts.TITLE).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(RestartBookTexts.ASK_TITLE).assertExists()
        compose.onNodeWithText(RestartBookTexts.askText(TITLE)).assertExists()
        compose.onNodeWithText(RestartBookTexts.SAVE_BACKUP).assertExists()
        compose.onNodeWithText(RestartBookTexts.CANCEL).performClick()
        waitForText(RestartBookTexts.CANCELLED)
        assertEquals(120, points())

        // Dialog 2, cancelled.
        compose.onNodeWithText(RestartBookTexts.TITLE).performScrollTo().performClick()
        compose.onNodeWithText(DebugMarker.label(RestartBookTexts.WITHOUT_BACKUP)).performClick()
        compose.onNodeWithText(RestartBookTexts.CONFIRM_TITLE).assertExists()
        compose.onNodeWithText(RestartBookTexts.CONFIRM_TEXT_WITHOUT_BACKUP).assertExists()
        compose.onNodeWithText(RestartBookTexts.CANCEL).performClick()
        compose.waitForIdle()
        assertEquals(120, points())
        assertEquals(0, slotStart())

        // Confirmed: the book starts anew.
        compose.onNodeWithText(RestartBookTexts.TITLE).performScrollTo().performClick()
        compose.onNodeWithText(DebugMarker.label(RestartBookTexts.WITHOUT_BACKUP)).performClick()
        compose.onNodeWithText(RestartBookTexts.CONFIRM).performClick()
        waitForText(RestartBookTexts.restarted(TITLE))
        assertEquals(0, points())
        assertEquals(1, slotStart())
        waitForText(RestartBookTexts.NOTHING_TO_RESET)
        // The start screen asks for avatar 1 again ("Wähle deinen Avatar").
        val quiz = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.needsChoice }
        assertEquals(FigureTexts.choiceTitle(false), FigureTexts.choiceTitle(quiz.figureState.value.choosingNext))
        assertEquals(1, quiz.figureState.value.avatarNumber)
    }

    /** A real book: dialog 1 announces the backup and goes on to it, never straight to the reset. */
    @Test
    fun aRealBookAsksForTheBackupFirst() {
        withProgress()
        showScreen()
        waitForEnabledButton()
        compose.onNodeWithText(RestartBookTexts.TITLE).performScrollTo().performClick()
        compose.onNodeWithText(RestartBookTexts.askText(TITLE)).assertExists()
        compose.onNodeWithText(RestartBookTexts.BACKUP_FIRST, substring = true).assertExists()
        compose.onNodeWithText(RestartBookTexts.EXAMPLE_BOOK_LOST, substring = true).assertDoesNotExist()
        compose.onNodeWithText(RestartBookTexts.SAVE_BACKUP).assertExists()
        compose.onNodeWithText(RestartBookTexts.CONFIRM).assertDoesNotExist()
        compose.onNodeWithText(RestartBookTexts.CANCEL).performClick()
        waitForText(RestartBookTexts.CANCELLED)
        assertEquals(120, points())
    }

    /** The example book is never in a backup: no backup step, and the dialog says it cannot be brought back. */
    @Test
    fun theExampleBookRestartsWithoutABackupStep() {
        val example = "Beispielbuch Englisch"
        runBlocking {
            VocabularyRepository(db, ExampleBook.loader(context)).ensureExampleBook()
            repository.setActiveBook(ExampleBook.ID)
            repository.chooseFigure(FigureKind.DRAGON)
            db.pointsDayDao().upsert(PointsDay("2026-10-09", DEVICE, risePoints = 120, updatedAt = 5L, bookId = ExampleBook.ID))
            db.pointsDayDao().upsert(PointsDay("2026-10-09", DEVICE, risePoints = 30, updatedAt = 5L, bookId = TEST_BOOK_ID))
        }
        showScreen()
        waitForEnabledButton()

        compose.onNodeWithText(RestartBookTexts.TITLE).performScrollTo().performClick()
        compose.onNodeWithText(RestartBookTexts.askText(example, isExampleBook = true)).assertExists()
        compose.onNodeWithText(RestartBookTexts.EXAMPLE_BOOK_LOST, substring = true).assertExists()
        // The page itself has "Sicherung exportieren"/"… wiederherstellen"; the dialog never mentions a backup.
        compose.onNode(hasText("Sicherung", substring = true) and hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.onNodeWithText(RestartBookTexts.SAVE_BACKUP).assertDoesNotExist()
        compose.onNodeWithText(DebugMarker.label(RestartBookTexts.WITHOUT_BACKUP)).assertDoesNotExist()

        compose.onNodeWithText(RestartBookTexts.CONFIRM).performClick()
        waitForText(RestartBookTexts.restarted(example))
        compose.onNodeWithText(RestartBookTexts.CONFIRM_TITLE).assertDoesNotExist()
        assertEquals(0, points())
        assertEquals(1, slotStart())
        // The other book is untouched. The page's view models stop first: they would still query the
        // switched book while the database closes after the test.
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        runBlocking { repository.setActiveBook(TEST_BOOK_ID) }
        assertEquals(30, points())
    }

    /**
     * The step after the system save dialog (which a test cannot drive): a cancelled dialog resets
     * nothing; a saved backup leads to dialog 2 and the file holds the old state.
     */
    @Test
    fun theBackupIsWrittenBeforeTheLastQuestion() {
        withProgress()
        showScreen()
        waitForEnabledButton()
        val viewModel = ViewModelProvider(compose.activity)[ManageProgressViewModel::class.java]

        compose.onNodeWithText(RestartBookTexts.TITLE).performScrollTo().performClick()
        compose.runOnUiThread {
            viewModel.startBackup()
            viewModel.backupChosen(null)
        }
        waitForText(RestartBookTexts.CANCELLED)
        compose.onNodeWithText(RestartBookTexts.CONFIRM_TITLE).assertDoesNotExist()
        assertEquals(120, points())

        val file = File(context.cacheDir, "manage_progress_backup.json").also { it.delete() }
        try {
            compose.onNodeWithText(RestartBookTexts.TITLE).performScrollTo().performClick()
            compose.runOnUiThread {
                viewModel.startBackup()
                viewModel.backupChosen(Uri.fromFile(file))
            }
            waitForText(RestartBookTexts.CONFIRM_TEXT)
            val backup = VocTrainerJson.parseSync(file.readText())
            assertEquals(120, backup.pointsDays.sumOf { it.risePoints })
            assertEquals(1, backup.figures.size)
            assertEquals(120, points())

            compose.onNodeWithText(RestartBookTexts.CONFIRM).performClick()
            waitForText(RestartBookTexts.restarted(TITLE))
            assertEquals(0, points())
        } finally {
            file.delete()
        }
    }
}
