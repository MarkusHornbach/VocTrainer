package io.github.markushornbach.voctrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.WordType

/** "Vokabeln editieren": filterable list of all vocabulary entries. */
@Composable
fun VocabularyListScreen(
    viewModel: VocabularyEditorViewModel,
    onBack: () -> Unit,
    onEdit: (Vocabulary) -> Unit
) {
    BackHandler(onBack = onBack)
    val state by viewModel.listState.collectAsState()
    val expanded by viewModel.filtersExpanded.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val activeCount = state.entries.count { !it.isDeleted }

    LaunchedEffect(message) {
        val shown = message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = shown.text,
            actionLabel = if (shown.undoDeleteId != null) "Rückgängig" else null,
            // With an action, Material 3 would otherwise keep the snackbar open indefinitely.
            duration = if (shown.undoDeleteId != null) SnackbarDuration.Long else SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) shown.undoDeleteId?.let(viewModel::undoDelete)
        viewModel.clearMessage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackTopBar(title = "Vokabeln editieren", onBack = onBack, debugOnly = true) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // One LazyColumn for filters and rows: the filters scroll away, and there is no nested scrolling.
        val listState = rememberLazyListState()
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "filters") {
                EditorFilterCard(state, expanded, viewModel)
            }
            item(key = "count") {
                Text(
                    text = VocabularyEditor.countLabel(state.entries.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (state.loaded && state.entries.isEmpty()) {
                item(key = "empty") { Text("Keine Vokabeln gefunden.") }
            }
            items(state.entries, key = { it.id }) { entry ->
                EditorRow(
                    entry = entry,
                    onClick = { onEdit(entry) },
                    onRestore = { if (!viewModel.restore(entry)) onEdit(entry) }
                )
            }
        }
        ScrollHint(listState.canScrollForward)
        }
    }
}

@Composable
private fun EditorFilterCard(state: EditorListState, expanded: Boolean, viewModel: VocabularyEditorViewModel) {
    val filter = state.filter
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Filter einklappen" else "Filter ausklappen") {
                    viewModel.toggleFilters()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (expanded) "Filter" else VocabularyEditor.summary(filter, state.partOptions),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = filter.search,
                    onValueChange = viewModel::setSearch,
                    label = { Text("Suche") },
                    supportingText = { Text("Englisch oder Deutsch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                UnitPartPageFilters(
                    unitChoices = state.unitChoices,
                    partOptions = state.partOptions,
                    pages = state.pages,
                    filter = filter.selection,
                    onSelectUnit = viewModel::selectUnit,
                    onTogglePart = viewModel::togglePart,
                    onSelectAllParts = viewModel::selectAllParts,
                    onSelectPageFrom = viewModel::selectPageFrom,
                    onSelectPageTo = viewModel::selectPageTo
                )
        ChipGroup(title = "Lernlevel") {
                    (0..Leitner.MAX_LEVEL).forEach { level ->
                        FilterChip(
                            selected = level in filter.levels,
                            onClick = { viewModel.toggleLevel(level) },
                            label = { Text(level.toString()) }
                        )
                    }
                }
                ChipGroup(title = "Wortart") {
                    EDITOR_WORD_TYPES.forEach { type ->
                        FilterChip(
                            selected = type in filter.types,
                            onClick = { viewModel.toggleType(type) },
                            label = { Text(wordTypeLabel(type)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.toggleable(
                        value = filter.showIgnored,
                        role = Role.Switch,
                        onValueChange = viewModel::setShowIgnored
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ignorierte anzeigen", modifier = Modifier.weight(1f))
                    Switch(checked = filter.showIgnored, onCheckedChange = null)
                }
                TextButton(
                    onClick = viewModel::resetFilters,
                    enabled = filter != EditorFilter(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Filter zurücksetzen")
                }
            }
        }
    }
}

@Composable
private fun EditorRow(entry: Vocabulary, onClick: () -> Unit, onRestore: () -> Unit) {
    val rowModifier = if (entry.isDeleted) Modifier.fillMaxWidth() else Modifier
        .fillMaxWidth()
        .clickable(onClickLabel = "Bearbeiten", onClick = onClick)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.english, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(entry.germanMeanings.first(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = VocabularyEditor.location(entry) + " · Level ${entry.leitnerLevel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.effectiveType == WordType.PHRASE) {
                    Text(
                        text = "Redewendung",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (entry.isDeleted) {
                OutlinedButton(onClick = onRestore) { Text("Wiederherstellen") }
            }
        }
    }
}

/** Edit page for one entry. [onClose] returns to the list. */
@Composable
fun VocabularyEditScreen(viewModel: VocabularyEditorViewModel, onClose: () -> Unit) {
    val editing by viewModel.editing.collectAsState()
    val state = editing
    if (state == null) {
        // E.g. after the app was restarted on this page: nothing to edit any more.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = {
        if (state.hasChanges) {
            confirmDiscard = true
        } else {
            viewModel.stopEditing()
            onClose()
        }
    }
    BackHandler(onBack = leave)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Änderungen verwerfen?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.stopEditing()
                    onClose()
                }) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Weiter bearbeiten") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackTopBar(title = "Vokabel bearbeiten", onBack = leave, debugOnly = true) }
    ) { innerPadding ->
        EditForm(
            state = state,
            onChange = viewModel::updateInput,
            onSave = { viewModel.save(onClose) },
            onCancel = leave,
            onDelete = { viewModel.deleteEditing(onClose) },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun EditForm(
    state: EditState,
    onChange: (EditInput) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = state.input
    val errors = state.validation.errors
    fun shown(error: String?) = error.takeIf { state.showErrors }

    ScrollColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.restoring) Text(RESTORE_WITH_CORE_WORD_HINT, style = MaterialTheme.typography.bodyMedium)
        FormField("Englisch", input.english, shown(errors.english)) { onChange(input.withEnglish(it)) }
        CoreWordSection(input.english, input.coreWord, shown(errors.coreWord)) {
            onChange(input.copy(coreWord = CoreWordChoice.tap(it)))
        }
        FormField(
            label = "Deutsch",
            value = input.german,
            error = shown(errors.german),
            hint = "Mehrere Bedeutungen mit ; trennen – die erste ist die Hauptbedeutung",
            singleLine = false
        ) { onChange(input.copy(german = it)) }
        FormField(
            label = "Unit",
            value = input.unit,
            error = shown(errors.unit) ?: errors.duplicate,
            keyboardType = KeyboardType.Number
        ) { onChange(input.copy(unit = it)) }
        FormField("Part (optional)", input.part, null, hint = "Leer = Einstieg") { onChange(input.copy(part = it)) }
        FormField("Seite (optional)", input.page, shown(errors.page), keyboardType = KeyboardType.Number) {
            onChange(input.copy(page = it))
        }
        ChoiceDropdown(
            label = "Wortart",
            choices = listOf<WordType?>(null) + EDITOR_WORD_TYPES,
            selected = input.wordType,
            choiceLabel = { type -> type?.let(::wordTypeLabel) ?: "Automatisch" },
            enabled = true,
            onSelect = { onChange(input.copy(wordType = it)) },
            modifier = Modifier.fillMaxWidth()
        )
        FormField("Extra (optional)", input.extra, null, singleLine = false) { onChange(input.copy(extra = it)) }
        ChipGroup(title = "Lernlevel") {
            (0..Leitner.MAX_LEVEL).forEach { level ->
                FilterChip(
                    selected = input.level == level,
                    onClick = { onChange(input.copy(level = level)) },
                    enabled = level <= state.maxLevel,
                    label = { Text(level.toString()) }
                )
            }
        }
        if (state.maxLevel < Leitner.MAX_LEVEL) {
            Text(
                text = "Das Lernlevel kann nur gesenkt werden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        shown(errors.level)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) { Text("Abbrechen") }
            Button(
                onClick = onSave,
                enabled = errors.duplicate == null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) { Text(if (state.restoring) "Wiederherstellen" else "Speichern") }
        }
        if (!state.restoring) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Löschen") }
        }
    }
}

/** Top of the edit page when an ignored entry without "Wort zum Tippen" is restored. */
const val RESTORE_WITH_CORE_WORD_HINT =
    "Diese Vokabel hat noch kein Wort zum Tippen. Tippe unten ein Wort an, dann kannst du sie wiederherstellen."

@Composable
private fun FormField(
    label: String,
    value: String,
    error: String?,
    hint: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: hint)?.let { text -> { Text(text) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}
