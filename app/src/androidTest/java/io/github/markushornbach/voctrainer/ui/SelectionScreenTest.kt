package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.IrregularVerb
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import io.github.markushornbach.voctrainer.settings.QuizMode
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

/**
 * The selection screen in the real app (in-memory database, own settings): reset rules, page list,
 * mode title and the draft with "Auswahl übernehmen" / "Zurück".
 */
@RunWith(AndroidJUnit4::class)
class SelectionScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_selection_screen_settings"
        const val DEVICE = "test_selection_screen_device"
        const val TIMEOUT = 5_000L
    }

    // Unit 1: "Einstieg" on 10, Part A on 11–12, Part B on 12–13 (page 12 is shared). Unit 2: Part A on 20–21.
    private val words = listOf(
        voc("one", "eins", 1, null, 10),
        voc("two", "zwei", 1, "A", 11),
        voc("three", "drei", 1, "A", 11),
        voc("four", "vier", 1, "A", 12),
        voc("five", "fünf", 1, "B", 12),
        voc("six", "sechs", 1, "B", 13),
        voc("seven", "sieben", 2, "A", 20),
        voc("eight", "acht", 2, "A", 21)
    )

    private val verbs = listOf(
        IrregularVerb(infinitive = "go", simplePast = "went", germanMeanings = listOf("gehen"), unit = 1, part = "A", page = 11),
        IrregularVerb(infinitive = "see", simplePast = "saw", germanMeanings = listOf("sehen"), unit = 1, part = "B", page = 12),
        IrregularVerb(infinitive = "eat", simplePast = "ate", germanMeanings = listOf("essen"), unit = 2, part = "A", page = 20)
    )

    private fun voc(english: String, german: String, unit: Int, part: String?, page: Int) =
        Vocabulary(english = english, germanMeanings = listOf(german), unit = unit, part = part, page = page, coreWord = english)

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            // No one-time notices over the screen.
            markAllTipsDone()
        }
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            db.vocabularyDao().insertAll(words.inTestBook())
            db.irregularVerbDao().insertAll(verbs.inTestBook())
            val now = System.currentTimeMillis()
            db.figureDao().upsert(
                Figure(slot = 0, kind = FigureKind.entries.first().key, pointsAtStart = 0, startedAt = now, updatedAt = now, bookId = TEST_BOOK_ID)
            )
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

    private val viewModel: QuizViewModel
        get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    /** The vocabulary selection saved in the active book. */
    private fun savedFilter(): QuizFilter? = runBlocking { VocabularyRepository(db).loadQuizFilter(QuizMode.VOCABULARY) }

    /** "Auswahl übernehmen" saves in the background: waits until [expected] is stored. */
    private fun assertSaved(expected: QuizFilter) {
        compose.waitUntil(5_000) { savedFilter() == expected }
        assertEquals(expected, savedFilter())
    }

    private val filter: QuizFilter get() = viewModel.startState.value.filter

    private val selectionOpen: Boolean get() = viewModel.filtersExpanded.value

    private fun start() {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        compose.waitUntil(TIMEOUT) { viewModel.startState.value.loaded }
    }

    private fun openSelection() {
        compose.onNode(
            SemanticsMatcher("opens the selection") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Auswahl ändern" }
        ).performClick()
        compose.waitForIdle()
        assertTrue(selectionOpen)
    }

    private fun inPopup(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isPopup()))

    private fun chooseUnit(label: String) {
        compose.onNodeWithContentDescription("Unit").performClick()
        inPopup(label).performClick()
        compose.waitForIdle()
    }

    private fun choosePageFrom(page: Int) {
        compose.onNodeWithContentDescription("Seiten von").performClick()
        inPopup(page.toString()).performClick()
        compose.waitForIdle()
    }

    /** The entries of the "Seiten von" list (without "Alle Seiten"); closes the list again. */
    private fun offeredPages(candidates: IntRange = 1..40): List<Int> {
        compose.onNodeWithContentDescription("Seiten von").performClick()
        compose.waitForIdle()
        val pages = candidates.filter { page ->
            compose.onAllNodes(hasText(page.toString()) and hasAnyAncestor(isPopup())).fetchSemanticsNodes().isNotEmpty()
        }
        inPopup("Alle Seiten").assertExists()
        device.pressBack()
        compose.waitForIdle()
        return pages
    }

    private fun tapPart(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    private fun dialogShown(): Boolean =
        compose.onAllNodes(hasText(SelectionTexts.CHANGED_QUESTION)).fetchSemanticsNodes().isNotEmpty()

    // Part 1: filter rules in the UI.

    @Test
    fun changingTheUnitResetsPartsAndPagesAndAlleUnitsHidesParts() {
        start()
        openSelection()
        chooseUnit("Unit 1")
        tapPart("A")
        choosePageFrom(11)
        assertEquals(QuizFilter(UnitChoice.Number(1), setOf("A"), 11), filter)

        chooseUnit("Unit 2")
        assertEquals(QuizFilter(UnitChoice.Number(2)), filter)
        compose.onNodeWithText("Alle Seiten").assertExists()

        tapPart("A")
        choosePageFrom(21)
        chooseUnit("Alle Units")
        assertEquals(QuizFilter(), filter)
        // The Part row is hidden completely.
        compose.onNodeWithText("Part").assertDoesNotExist()
        compose.onNodeWithTag("alleChipParts").assertDoesNotExist()
        compose.onNodeWithText("Einstieg").assertDoesNotExist()
        compose.onNodeWithText("B").assertDoesNotExist()
    }

    @Test
    fun changingThePartsResetsThePagesAndThePageListFollowsUnitAndParts() {
        start()
        openSelection()
        assertEquals(listOf(10, 11, 12, 13, 20, 21), offeredPages())
        chooseUnit("Unit 1")
        assertEquals(listOf(10, 11, 12, 13), offeredPages())
        tapPart("A")
        assertEquals(listOf(11, 12), offeredPages())
        choosePageFrom(12)
        // Page 12 is shared: only the word of Part A on it.
        assertEquals(1, viewModel.startState.value.wordCount)

        tapPart("B")
        assertEquals(QuizFilter(UnitChoice.Number(1), setOf("A", "B")), filter)
        assertEquals(listOf(11, 12, 13), offeredPages())
        tapPart("Einstieg")
        // Every part ticked is "Alle".
        assertEquals(QuizFilter(UnitChoice.Number(1)), filter)
        tapPart("B")
        assertEquals(listOf(12, 13), offeredPages())
        choosePageFrom(12)
        assertEquals(1, viewModel.startState.value.wordCount)
        // Seiten never change Unit or Parts.
        assertEquals(setOf<String?>("B"), filter.parts)
    }

    @Test
    fun verbModeHasTheSameRules() {
        settings.setQuizMode(QuizMode.VERBS)
        start()
        openSelection()
        compose.onNodeWithTag(SelectionTexts.MODE_TITLE_TAG).assertExists()
        compose.onNodeWithText("Verben").assertExists()
        compose.onNodeWithText("Vokabeln").assertDoesNotExist()
        chooseUnit("Unit 1")
        tapPart("B")
        assertEquals(listOf(12), offeredPages())
        choosePageFrom(12)
        assertEquals(1, viewModel.startState.value.wordCount)
        chooseUnit("Unit 2")
        assertEquals(QuizFilter(UnitChoice.Number(2)), filter)
    }

    // Part 2: labels, mode title, keeping or discarding the draft.

    @Test
    fun labelsAndPlainModeTitle() {
        start()
        openSelection()
        compose.onNodeWithText("Alle Units").assertExists()
        compose.onNodeWithText("Alle Seiten").assertExists()
        compose.onNodeWithTag(SelectionTexts.MODE_TITLE_TAG).assertHasNoClickAction()
        compose.onNodeWithText("Vokabeln").assertExists()
        // The other mode is hidden; the screen is only left with its own buttons.
        compose.onNodeWithText("Verben").assertDoesNotExist()
        compose.onNodeWithContentDescription("Einstellungen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hilfe").assertDoesNotExist()
        compose.onNodeWithText(SelectionTexts.APPLY).assertExists()
        compose.onNodeWithText(SelectionTexts.BACK).assertExists()
    }

    @Test
    fun applyKeepsTheSelection() {
        start()
        openSelection()
        chooseUnit("Unit 1")
        tapPart("B")
        compose.onNodeWithText(SelectionTexts.APPLY).performClick()
        compose.waitForIdle()
        assertFalse(selectionOpen)
        assertFalse(dialogShown())
        val expected = QuizFilter(UnitChoice.Number(1), setOf("B"))
        assertEquals(expected, filter)
        assertSaved(expected)
        // Back on the start screen: both modes again.
        compose.onNodeWithText("Verben").assertExists()
    }

    @Test
    fun backWithoutChangeLeavesWithoutDialog() {
        start()
        openSelection()
        compose.onNodeWithText(SelectionTexts.BACK).performClick()
        compose.waitForIdle()
        assertFalse(selectionOpen)
        assertFalse(dialogShown())

        // Changed and changed back is no change either.
        openSelection()
        chooseUnit("Unit 1")
        chooseUnit("Alle Units")
        compose.onNodeWithText(SelectionTexts.BACK).performClick()
        compose.waitForIdle()
        assertFalse(selectionOpen)
        assertFalse(dialogShown())
    }

    @Test
    fun backWithChangeAsksAndDiscardRestoresTheSelection() {
        val saved = QuizFilter(UnitChoice.Number(1), setOf("A"))
        runBlocking { VocabularyRepository(db).saveQuizFilter(QuizMode.VOCABULARY, saved) }
        start()
        assertEquals(saved, filter)
        openSelection()
        chooseUnit("Unit 2")
        compose.onNodeWithText(SelectionTexts.BACK).performClick()
        compose.waitForIdle()
        assertTrue(dialogShown())
        assertTrue(selectionOpen)
        compose.onNodeWithText(SelectionTexts.DIALOG_DISCARD).performClick()
        compose.waitForIdle()
        assertFalse(selectionOpen)
        assertEquals(saved, filter)
        assertSaved(saved)
    }

    @Test
    fun applyInTheDialogKeepsTheSelection() {
        start()
        openSelection()
        chooseUnit("Unit 2")
        compose.onNodeWithText(SelectionTexts.BACK).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(SelectionTexts.DIALOG_APPLY).performClick()
        compose.waitForIdle()
        assertFalse(selectionOpen)
        assertEquals(QuizFilter(UnitChoice.Number(2)), filter)
        assertSaved(QuizFilter(UnitChoice.Number(2)))
    }

    @Test
    fun systemBackWorksLikeZurueck() {
        start()
        openSelection()
        device.pressBack()
        compose.waitForIdle()
        assertFalse(selectionOpen)
        assertFalse(dialogShown())

        openSelection()
        chooseUnit("Unit 1")
        device.pressBack()
        compose.waitForIdle()
        assertTrue(dialogShown())
        compose.onNodeWithText(SelectionTexts.DIALOG_DISCARD).performClick()
        compose.waitForIdle()
        assertFalse(selectionOpen)
        assertEquals(QuizFilter(), filter)
    }

    @Test
    fun dismissingTheDialogStaysOnTheSelectionScreen() {
        start()
        openSelection()
        chooseUnit("Unit 1")
        compose.onNodeWithText(SelectionTexts.BACK).performClick()
        compose.waitForIdle()
        assertTrue(dialogShown())
        // Back closes only the dialog.
        device.pressBack()
        compose.waitForIdle()
        assertFalse(dialogShown())
        assertTrue(selectionOpen)
        assertEquals(UnitChoice.Number(1), filter.unit)

        // Tapping outside the dialog, too.
        compose.onNodeWithText(SelectionTexts.BACK).performClick()
        compose.waitForIdle()
        assertTrue(dialogShown())
        device.click(device.displayWidth / 2, device.displayHeight / 8)
        compose.waitForIdle()
        assertFalse(dialogShown())
        assertTrue(selectionOpen)
        assertEquals(UnitChoice.Number(1), filter.unit)
        // Nothing was saved yet.
        assertEquals(null, savedFilter())
    }
}
