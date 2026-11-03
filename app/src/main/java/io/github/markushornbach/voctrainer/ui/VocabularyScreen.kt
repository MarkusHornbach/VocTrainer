package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.markushornbach.voctrainer.data.BookSummary
import io.github.markushornbach.voctrainer.settings.AppSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Google Drive does not always report .json files as application/json.
internal val JSON_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

@Composable
fun VocabularyScreen(
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    /** The notices of "Buch neu beginnen" (the page's snackbar). */
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: VocabularyViewModel = viewModel()
) {
    val hasOwnBook by viewModel.hasOwnBook.collectAsState()
    val message by viewModel.message.collectAsState()
    val pendingRestore by viewModel.pendingRestore.collectAsState()
    val pendingBookSwitch by viewModel.pendingBookSwitch.collectAsState()
    val books by viewModel.books.collectAsState()
    var showEntryDialog by rememberSaveable { mutableStateOf(false) }

    val vocabularyImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importVocabulary) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportSync) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::chooseRestoreFile) }

    // No tip under a dialog.
    if (pendingRestore != null || message != null || pendingBookSwitch != null || showEntryDialog) BlockTips()

    pendingRestore?.let { pending ->
        RestoreConfirmDialog(
            contents = RestoreTexts.contents(pending.info),
            warning = viewModel.restoreWarning(pending.info),
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("OK") }
            },
            text = { Text(text) }
        )
    }

    // After the import message is closed.
    pendingBookSwitch?.takeIf { message == null }?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::declineBookSwitch,
            text = { Text(BookTexts.switchQuestion(pending.title)) },
            confirmButton = { TextButton(onClick = viewModel::confirmBookSwitch) { Text(BookTexts.SWITCH_YES) } },
            dismissButton = { TextButton(onClick = viewModel::declineBookSwitch) { Text(BookTexts.SWITCH_NO) } }
        )
    }

    if (showEntryDialog) {
        ManualEntryDialog(
            onDismiss = { showEntryDialog = false },
            onSave = { input, onError ->
                viewModel.addVocabulary(input) { error ->
                    if (error == null) showEntryDialog = false else onError(error)
                }
            }
        )
    }

    ScrollColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // What the sections do is on the info page "Bücher und Sicherung" (both headers open it).
        var showInfo by rememberSaveable { mutableStateOf(false) }
        if (showInfo) InfoPageDialog(InfoPage.BOOKS, InfoPageData()) { showInfo = false }

        SectionHeader(ManageVocabularyTexts.BOOKS_TITLE, onInfo = { showInfo = true }, infoTag = BOOKS_INFO_TAG)
        books?.takeIf { it.isNotEmpty() }?.let { list -> BookList(list, onSelect = viewModel::selectBook) }
        // Highlighted until the first own book exists; afterwards the backup is the next step.
        HighlightButton(
            highlighted = !hasOwnBook,
            onClick = { vocabularyImportLauncher.launch(JSON_MIME_TYPES) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(ManageVocabularyTexts.IMPORT)
        }
        if (BuildConfig.DEBUG) {
            DebugButton(
                onClick = { showEntryDialog = true },
                label = "Vokabel hinzufügen",
                modifier = Modifier.fillMaxWidth()
            )
            DebugButton(
                onClick = onOpenEditor,
                label = "Vokabeln editieren",
                modifier = Modifier.fillMaxWidth()
            )
            EditButtonSwitch()
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionHeader(ManageVocabularyTexts.BACKUP_TITLE, onInfo = { showInfo = true }, infoTag = BACKUP_INFO_TAG)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HighlightButton(
                highlighted = hasOwnBook,
                onClick = { backupExportLauncher.launch(backupFileName(LocalDate.now(), BuildConfig.DEBUG)) },
                modifier = Modifier.weight(1f)
            ) {
                Text(ManageVocabularyTexts.EXPORT)
            }
            HighlightButton(
                highlighted = hasOwnBook,
                onClick = { restoreLauncher.launch(JSON_MIME_TYPES) },
                modifier = Modifier.weight(1f)
            ) {
                Text(ManageVocabularyTexts.RESTORE)
            }
        }

        // Last on the page: it resets the learning state of the active book.
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        RestartBookSection(snackbarHostState)
    }
}

/** A filled button while [highlighted], otherwise an outlined one. */
@Composable
private fun HighlightButton(
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    if (highlighted) Button(onClick = onClick, modifier = modifier, content = content)
    else OutlinedButton(onClick = onClick, modifier = modifier, content = content)
}

/** A section title with its ⓘ at the far right. */
@Composable
internal fun SectionHeader(title: String, onInfo: () -> Unit, infoTag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        InfoButton(onClick = onInfo, modifier = Modifier.testTag(infoTag))
    }
}

/** The button labels of "Vokabeln verwalten" (also named on the info page "Bücher und Sicherung"). */
object ManageVocabularyTexts {
    const val IMPORT = "Vokabeln importieren"
    const val EXPORT = "Sicherung exportieren"
    const val RESTORE = "Sicherung wiederherstellen"
    const val BOOKS_TITLE = BookTexts.LIST_TITLE
    const val BACKUP_TITLE = "Sicherung"
}

/** Test tags of the ⓘ next to the headers "Bücher" and "Sicherung". */
const val BOOKS_INFO_TAG = "books_info"
const val BACKUP_INFO_TAG = "backup_info"

/** Test tag of a book row; the book id follows. */
const val BOOK_ROW_TAG = "book_row_"

/**
 * The books with their counts (which ones: see listedBooks); the active one is marked.
 * Tapping another book makes it active (only what is shown changes).
 */
@Composable
private fun BookList(books: List<BookSummary>, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        for (summary in books) {
            val book = summary.book
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = book.isActive, role = Role.RadioButton, onClick = { onSelect(book.id) })
                    .testTag(BOOK_ROW_TAG + book.id),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = book.isActive, onClick = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        BookTexts.counts(summary.words, summary.learnedWords),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (book.isActive) {
                    Text(BookTexts.ACTIVE, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Suggested name of a backup file: "YYMMDD_" first (sorts by date); the test app (debug build) adds "debug_" after it. */
internal fun backupFileName(date: LocalDate, debug: Boolean): String =
    date.format(DateTimeFormatter.ofPattern("yyMMdd")) + "_" + (if (debug) "debug_" else "") + "voctrainer-sicherung.json"

/** Asks before "Sicherung wiederherstellen" replaces anything; shows what the file contains. */
@Composable
private fun RestoreConfirmDialog(contents: String, warning: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sicherung wiederherstellen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(RestoreTexts.CONFIRM)
                Text(contents)
                warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Wiederherstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/** "Bearbeiten-Knopf in der Abfrage": pencil on vocabulary questions; device setting, default off. */
@Composable
private fun EditButtonSwitch() {
    val context = LocalContext.current
    val settings = remember { AppSettings.getInstance(context) }
    val enabled by settings.editButtonInQuiz.collectAsState()
    DebugSwitchRow(
        title = EDIT_BUTTON_SETTING_TITLE,
        description = EDIT_BUTTON_SETTING_DESCRIPTION,
        checked = enabled,
        onChange = settings::setEditButtonInQuiz
    )
}

const val EDIT_BUTTON_SETTING_TITLE = "Bearbeiten-Knopf in der Abfrage"
const val EDIT_BUTTON_SETTING_DESCRIPTION = "Zeigt bei Vokabelfragen einen Stift, der die Vokabel zum Bearbeiten öffnet."

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSave: (input: ManualEntryInput, onError: (ManualEntryResult.Invalid) -> Unit) -> Unit
) {
    var english by rememberSaveable { mutableStateOf("") }
    var german by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var part by rememberSaveable { mutableStateOf("") }
    var extra by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf("") }
    var coreWord by rememberSaveable { mutableStateOf<String?>(null) }
    var coreWordTapped by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var errorField by rememberSaveable { mutableStateOf(ManualEntryField.OTHER) }
    val choice = CoreWordChoice(coreWord, coreWordTapped)
    fun choose(new: CoreWordChoice) {
        coreWord = new.word
        coreWordTapped = new.tapped
    }
    fun errorAt(field: ManualEntryField) = error.takeIf { errorField == field }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vokabel hinzufügen") },
        text = {
            ScrollColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = english,
                    onValueChange = {
                        english = it
                        choose(choice.afterEnglishChange(it))
                    },
                    label = { Text("Englisch") },
                    isError = errorAt(ManualEntryField.ENGLISH) != null,
                    supportingText = errorAt(ManualEntryField.ENGLISH)?.let { text -> { Text(text) } },
                    singleLine = true
                )
                CoreWordSection(english, choice, errorAt(ManualEntryField.CORE_WORD)) {
                    choose(CoreWordChoice.tap(it))
                }
                OutlinedTextField(
                    value = german,
                    onValueChange = { german = it },
                    label = { Text("Deutsch") },
                    supportingText = { Text("Mehrere Bedeutungen mit ; trennen – die erste ist die Hauptbedeutung") }
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = part,
                    onValueChange = { part = it },
                    label = { Text("Part (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = page,
                    onValueChange = { page = it },
                    label = { Text("Seite (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("Extra (optional)") }
                )
                errorAt(ManualEntryField.OTHER)?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = null
                onSave(ManualEntryInput(english, german, unit, part, extra, page, choice)) {
                    error = it.message
                    errorField = it.field
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
