package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice

/** Unit dropdown, Part chips and page range; shared by the quiz start screen and the vocabulary editor. */
@Composable
internal fun UnitPartPageFilters(
    unitChoices: List<UnitChoice>,
    partOptions: List<String?>,
    pages: List<Int>,
    filter: QuizFilter,
    onSelectUnit: (UnitChoice) -> Unit,
    onTogglePart: (String?) -> Unit,
    onSelectAllParts: () -> Unit,
    onSelectPageFrom: (Int?) -> Unit,
    onSelectPageTo: (Int?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FILTER_BLOCK_SPACING)) {
        FilterBlock(title = "Unit") {
            AllChip(category = "Units", selected = filter.unit == UnitChoice.All) { onSelectUnit(UnitChoice.All) }
            ChoiceDropdown(
                label = "Unit",
                shortLabel = null,
                choices = unitChoices,
                selected = filter.unit,
                choiceLabel = { if (it == UnitChoice.All) "Alle Units" else unitLabel(it) },
                enabled = true,
                onSelect = onSelectUnit,
                modifier = Modifier.weight(1f)
            )
        }
        // Parts belong to one unit: with "Alle Units" the row is hidden.
        if (filter.unit != UnitChoice.All && partOptions.isNotEmpty()) {
            ChipGroup(title = "Part") {
                AllChip(category = "Parts", selected = filter.parts.isEmpty(), onClick = onSelectAllParts)
                partOptions.forEach { part ->
                    FilterChip(
                        selected = part in filter.parts,
                        onClick = { onTogglePart(part) },
                        label = { Text(partLabel(part)) },
                        modifier = Modifier.height(FILTER_FIELD_HEIGHT),
                        colors = filterChipColors()
                    )
                }
            }
        }
        FilterBlock(title = "Seiten") {
            // "bis" can only be set together with "von", so "von" tells whether a range is set.
            AllChip(category = "Seiten", selected = filter.pageFrom == null) { onSelectPageFrom(null) }
            ChoiceDropdown(
                label = "Seiten von",
                shortLabel = "von",
                choices = listOf<Int?>(null) + pages,
                selected = filter.pageFrom,
                choiceLabel = { it?.toString() ?: "Alle Seiten" },
                enabled = pages.isNotEmpty(),
                onSelect = onSelectPageFrom,
                modifier = Modifier.weight(1f)
            )
            val from = filter.pageFrom
            ChoiceDropdown(
                label = "Seiten bis",
                shortLabel = "bis",
                choices = listOf<Int?>(null) + pages.filter { from != null && it > from },
                selected = filter.pageTo,
                choiceLabel = { it?.toString() ?: "–" },
                enabled = from != null,
                onSelect = onSelectPageTo,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private val FILTER_BLOCK_SPACING = 16.dp
private val FILTER_FIELD_HEIGHT = 56.dp

/** Selected chips use the same blue as the selected "Vokabeln" tile. */
@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
)

/**
 * The "Alle" chip at the start of the Unit, Part and Seiten rows. It only mirrors the filter (on =
 * nothing narrowed down) and can only be tapped while off, so there is never an empty selection.
 */
@Composable
internal fun AllChip(category: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { if (!selected) onClick() },
        label = { Text("Alle") },
        colors = filterChipColors(),
        modifier = Modifier
            .height(FILTER_FIELD_HEIGHT)
            .testTag("alleChip$category")
            .semantics { contentDescription = "Alle $category, ${if (selected) "ausgewählt" else "nicht ausgewählt"}" }
    )
}

/** A small title above a wrapping row of chips. */
@Composable
internal fun ChipGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) { chips() }
    }
}

/** Same heading as [ChipGroup], above one line with an "Alle" chip and dropdowns, centred vertically. */
@Composable
private fun FilterBlock(title: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ChoiceDropdown(
    label: String,
    choices: List<T>,
    selected: T,
    choiceLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Floating label; [label] stays the accessible name even when this is null or shorter. */
    shortLabel: String? = label
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = choiceLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = shortLabel?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { if (shortLabel != label) contentDescription = label }
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choiceLabel(choice)) },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    }
                )
            }
        }
    }
}
