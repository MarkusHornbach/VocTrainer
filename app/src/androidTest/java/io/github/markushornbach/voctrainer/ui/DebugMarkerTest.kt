package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared test marker is visible on everything that exists only in the test app (debug build). */
@RunWith(AndroidJUnit4::class)
class DebugMarkerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase

    private companion object {
        const val SETTINGS = "test_debug_marker_settings"
        const val DEVICE = "test_debug_marker_device"
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
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        VocTrainerDatabase.replaceInstanceForTest(null)
        AppSettings.replaceInstanceForTest(null)
        db.closeAfterRunningQueries()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    private fun editorViewModel() = ViewModelProvider(compose.activity)[VocabularyEditorViewModel::class.java]

    @Test
    fun settingsCardTestwerkzeugeIsMarked() {
        assertEquals(true, BuildConfig.DEBUG)
        compose.setContent {
            VocTrainerTheme {
                SettingsScreen(
                    settings = AppSettings.getInstance(context), onBack = {}, onOpenManageVocabulary = {},
                    onOpenAppearance = {}, onOpenPractice = {}, onOpenLearning = {}, onOpenTestTools = {}, onOpenReleaseNotes = {}
                )
            }
        }
        compose.onNodeWithText(DebugMarker.label("Testwerkzeuge")).assertIsDisplayed()
        // The cards that also exist in the release app stay unmarked.
        compose.onNodeWithText("Vokabeln verwalten").assertIsDisplayed()
    }

    @Test
    fun vocabularyManagementDebugElementsAreMarkedAndReleaseOnesAreNot() {
        compose.setContent { VocTrainerTheme { VocabularyScreen(onOpenEditor = {}) } }
        compose.onNodeWithText(DebugMarker.label("Vokabel hinzufügen")).assertIsDisplayed()
        compose.onNodeWithText(DebugMarker.label("Vokabeln editieren")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(DebugMarker.label(EDIT_BUTTON_SETTING_TITLE)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(ManageVocabularyTexts.IMPORT).assertIsDisplayed()
        compose.onNodeWithText(ManageVocabularyTexts.EXPORT).assertIsDisplayed()
    }

    @Test
    fun testToolsScreenShowsTheBar() {
        compose.setContent { VocTrainerTheme { TestToolsScreen(onBack = {}, onShowTip = {}) } }
        compose.onNodeWithTag(DebugMarker.BAR_TAG).assertIsDisplayed()
        compose.onNodeWithText(DebugMarker.BAR_TEXT).assertIsDisplayed()
    }

    @Test
    fun vocabularyListScreenShowsTheBar() {
        val viewModel = editorViewModel()
        compose.setContent { VocTrainerTheme { VocabularyListScreen(viewModel, onBack = {}, onEdit = {}) } }
        compose.onNodeWithTag(DebugMarker.BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun vocabularyEditScreenShowsTheBar() {
        val entry = Vocabulary(
            id = "m", english = "house", germanMeanings = listOf("Haus"), unit = 1, coreWord = "house", bookId = TEST_BOOK_ID
        )
        runBlocking { db.vocabularyDao().insertAll(listOf(entry)) }
        val viewModel = editorViewModel()
        viewModel.startEditing(entry)
        compose.setContent { VocTrainerTheme { VocabularyEditScreen(viewModel, onClose = {}) } }
        compose.onNodeWithTag(DebugMarker.BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun screensThatAlsoExistInReleaseHaveNoBar() {
        compose.setContent { VocTrainerTheme { BackTopBar(title = "Vokabeln verwalten", onBack = {}) } }
        compose.onAllNodesWithTagCount(DebugMarker.BAR_TAG).let { assertEquals(0, it) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
}
