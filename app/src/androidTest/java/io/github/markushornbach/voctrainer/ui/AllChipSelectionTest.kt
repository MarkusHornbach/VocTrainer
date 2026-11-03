package io.github.markushornbach.voctrainer.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The uniform "Alle" chip in the Unit, Part and Seiten rows of the selection filters. */
@RunWith(AndroidJUnit4::class)
class AllChipSelectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val parts = listOf<String?>(null, "A", "B")
    private var filter by mutableStateOf(QuizFilter())

    private fun show() {
        compose.setContent {
            VocTrainerTheme {
                UnitPartPageFilters(
                    unitChoices = listOf(UnitChoice.All, UnitChoice.Number(1), UnitChoice.Number(2)),
                    partOptions = if (filter.unit == UnitChoice.All) emptyList() else parts,
                    pages = listOf(5, 6, 7),
                    filter = filter,
                    onSelectUnit = { filter = QuizSelection.withUnit(filter, it) },
                    onTogglePart = { filter = QuizSelection.togglePart(filter, it, parts) },
                    onSelectAllParts = { filter = QuizSelection.withAllParts(filter) },
                    onSelectPageFrom = { filter = QuizSelection.withPageFrom(filter, it) },
                    onSelectPageTo = { filter = filter.copy(pageTo = it) }
                )
            }
        }
    }

    @Test
    fun alleChipsStartOnForUnitAndSeiten() {
        show()
        compose.onNodeWithTag("alleChipUnits").assertIsSelected()
        compose.onNodeWithTag("alleChipSeiten").assertIsSelected()
    }

    @Test
    fun unitChipAndDropdownStayInSync() {
        show()
        compose.runOnIdle { filter = QuizSelection.withUnit(filter, UnitChoice.Number(1)) }
        compose.onNodeWithTag("alleChipUnits").assertIsNotSelected()
        compose.onNodeWithTag("alleChipUnits").performClick()
        compose.runOnIdle { assertEquals(UnitChoice.All, filter.unit) }
        compose.onNodeWithTag("alleChipUnits").assertIsSelected()
    }

    @Test
    fun partChipFollowsCombinationAndAlleClearsIt() {
        show()
        compose.runOnIdle { filter = QuizSelection.withUnit(filter, UnitChoice.Number(1)) }
        compose.onNodeWithTag("alleChipParts").assertIsSelected()
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("B").performClick()
        compose.onNodeWithTag("alleChipParts").assertIsNotSelected()
        compose.onNodeWithTag("alleChipParts").performClick()
        compose.runOnIdle { assertEquals(emptySet<String?>(), filter.parts) }
        compose.onNodeWithTag("alleChipParts").assertIsSelected()
        // Tapping "Alle" while on does nothing.
        compose.onNodeWithTag("alleChipParts").performClick()
        compose.onNodeWithTag("alleChipParts").assertIsSelected()
    }

    @Test
    fun seitenAlleAfterRangeResetsRange() {
        show()
        compose.runOnIdle { filter = QuizSelection.withPageFrom(filter, 5).copy(pageTo = 6) }
        compose.onNodeWithTag("alleChipSeiten").assertIsNotSelected()
        compose.onNodeWithTag("alleChipSeiten").performClick()
        compose.runOnIdle {
            assertNull(filter.pageFrom)
            assertNull(filter.pageTo)
        }
        compose.onNodeWithTag("alleChipSeiten").assertIsSelected()
    }

    @Test
    fun headingsExistAndOldTextLinksAreGone() {
        show()
        compose.runOnIdle { filter = QuizSelection.withUnit(filter, UnitChoice.Number(1)).let { QuizSelection.withPageFrom(it, 5) } }
        compose.onNodeWithText("Unit").assertExists()
        compose.onNodeWithText("Part").assertExists()
        compose.onNodeWithText("Seiten").assertExists()
        compose.onNodeWithText("Alle Units").assertDoesNotExist()
        compose.onNodeWithText("Alle Seiten").assertDoesNotExist()
    }

    @Test
    fun alleEntryInDropdownsResetsUnitAndSeiten() {
        show()
        compose.runOnIdle { filter = QuizSelection.withUnit(filter, UnitChoice.Number(1)).let { QuizSelection.withPageFrom(it, 5) } }
        compose.onNodeWithContentDescription("Seiten von").performClick()
        compose.onNode(hasText("Alle Seiten") and hasAnyAncestor(isPopup())).performClick()
        compose.runOnIdle { assertNull(filter.pageFrom) }
        compose.onNodeWithContentDescription("Unit").performClick()
        compose.onNode(hasText("Alle Units") and hasAnyAncestor(isPopup())).performClick()
        compose.runOnIdle { assertEquals(UnitChoice.All, filter.unit) }
    }

    @Test
    fun dropdownsSayAlleUnitsAndAlleSeiten() {
        show()
        compose.onNodeWithText("Alle Units").assertExists()
        compose.onNodeWithText("Alle Seiten").assertExists()
    }

    @Test
    fun partRowIsHiddenForAlleUnits() {
        show()
        compose.onNodeWithText("Part").assertDoesNotExist()
        compose.onNodeWithTag("alleChipParts").assertDoesNotExist()
        compose.onNodeWithText("A").assertDoesNotExist()
        compose.runOnIdle { filter = QuizSelection.withUnit(filter, UnitChoice.Number(1)) }
        compose.onNodeWithText("Part").assertExists()
        compose.onNodeWithTag("alleChipParts").assertIsSelected()
        compose.onNodeWithText("A").assertExists()
        compose.runOnIdle { filter = QuizSelection.withUnit(filter, UnitChoice.All) }
        compose.onNodeWithText("Part").assertDoesNotExist()
    }

    @Test
    fun partAndUnitChangesResetThePages() {
        show()
        compose.runOnIdle { filter = QuizSelection.withPageFrom(QuizSelection.withUnit(filter, UnitChoice.Number(1)), 5).copy(pageTo = 6) }
        compose.onNodeWithText("A").performClick()
        compose.runOnIdle {
            assertEquals(setOf<String?>("A"), filter.parts)
            assertNull(filter.pageFrom)
            filter = QuizSelection.withPageFrom(filter, 6)
        }
        compose.onNodeWithTag("alleChipParts").performClick()
        compose.runOnIdle {
            assertNull(filter.pageFrom)
            filter = QuizSelection.togglePart(QuizSelection.withPageFrom(filter, 7), "B", parts)
        }
        compose.onNodeWithTag("alleChipUnits").performClick()
        compose.runOnIdle { assertEquals(QuizFilter(), filter) }
    }
}
