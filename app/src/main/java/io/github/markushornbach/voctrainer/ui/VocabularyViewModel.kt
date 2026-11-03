package io.github.markushornbach.voctrainer.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.data.BackupBook
import io.github.markushornbach.voctrainer.data.BackupInfo
import io.github.markushornbach.voctrainer.data.BookImport
import io.github.markushornbach.voctrainer.data.BookSummary
import io.github.markushornbach.voctrainer.data.InvalidFileException
import io.github.markushornbach.voctrainer.data.VocTrainerJson
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.reminder.Reminder
import io.github.markushornbach.voctrainer.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val MAX_FILE_BYTES = 5 * 1024 * 1024

/** Where an error of "Vokabel hinzufügen" is shown. */
enum class ManualEntryField { ENGLISH, CORE_WORD, OTHER }

sealed interface ManualEntryResult {
    data class Valid(val vocabulary: Vocabulary) : ManualEntryResult
    data class Invalid(val message: String, val field: ManualEntryField = ManualEntryField.OTHER) : ManualEntryResult
}

data class ManualEntryInput(
    val english: String = "",
    val german: String = "",
    val unit: String = "",
    val part: String = "",
    val extra: String = "",
    val page: String = "",
    /** "Wort zum Tippen". */
    val coreWord: CoreWordChoice = CoreWordChoice()
)

fun parseManualEntry(input: ManualEntryInput): ManualEntryResult {
    val (english, german, unit, part, extra, page) = input
    if (english.isBlank()) return ManualEntryResult.Invalid("Bitte das englische Wort eingeben.", ManualEntryField.ENGLISH)
    CoreWordTexts.englishError(english)?.let { return ManualEntryResult.Invalid(it, ManualEntryField.ENGLISH) }
    val meanings = german.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    if (meanings.isEmpty()) {
        return ManualEntryResult.Invalid("Bitte mindestens eine deutsche Bedeutung eingeben.")
    }
    val unitNumber = unit.trim().toIntOrNull()
    if (unitNumber == null || unitNumber < 1) {
        return ManualEntryResult.Invalid("Bitte eine gültige Unit-Nummer eingeben.")
    }
    val pageNumber = page.trim().takeIf { it.isNotEmpty() }?.let { text ->
        text.toIntOrNull()?.takeIf { it >= 1 }
            ?: return ManualEntryResult.Invalid("Bitte eine gültige Seitenzahl eingeben oder das Feld leer lassen.")
    }
    val coreWord = input.coreWord.chosenIn(english)
        ?: return ManualEntryResult.Invalid(CoreWordTexts.CHOOSE, ManualEntryField.CORE_WORD)
    return ManualEntryResult.Valid(
        Vocabulary(
            english = english.trim(),
            germanMeanings = meanings,
            unit = unitNumber,
            part = part.trim().ifEmpty { null },
            extra = extra.trim().ifEmpty { null },
            page = pageNumber,
            coreWord = coreWord
        )
    )
}

/** A read backup file waiting for the confirmation of "Sicherung wiederherstellen". */
data class PendingRestore(val json: String, val info: BackupInfo)

/** A book just created by an import while another book is active: "jetzt zu diesem Buch wechseln?". */
data class PendingBookSwitch(val bookId: String, val title: String)

/** Texts of the books in "Vokabeln verwalten" and of the import. */
object BookTexts {
    const val LIST_TITLE = "Bücher"
    const val ACTIVE = "Aktiv"
    const val SWITCH_YES = "Ja"
    const val SWITCH_NO = "Nein"
    const val NO_BOOK_FOR_ENTRY = "Bitte zuerst Vokabeln importieren. Dabei wird das Buch angelegt."

    fun switchQuestion(title: String): String = "Neues Buch „$title“ – jetzt zu diesem Buch wechseln?"

    /** "120 Wörter, 35 gelernt" in the book list. */
    fun counts(words: Int, learned: Int): String = "${wordCount(words)}, $learned gelernt"

    private fun wordCount(words: Int): String = if (words == 1) "1 Wort" else "$words Wörter"

    /** First part of the import message: which book the file went into. */
    fun importedInto(title: String, isNewBook: Boolean): String =
        if (isNewBook) "Neues Buch „$title“ angelegt." else "Buch „$title“:"

    fun switched(title: String): String = "Du lernst jetzt mit „$title“."
}

const val BACKUP_SAVED = "Sicherung gespeichert."

/**
 * "Sicherung exportieren", also before "Buch neu beginnen": writes the backup file (every book
 * except the example book, and the learner's settings) to [uri]. Returns null on success, otherwise the German error text.
 */
internal suspend fun writeBackup(
    contentResolver: ContentResolver, uri: Uri, repository: VocabularyRepository, settings: AppSettings
): String? = try {
    val json = repository.exportSync(settings.toBackup())
    withContext(Dispatchers.IO) {
        val stream = contentResolver.openOutputStream(uri, "w") ?: throw IOException("Cannot open file")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }
    null
} catch (e: IOException) {
    "Sicherung fehlgeschlagen: Die Datei konnte nicht geschrieben werden."
} catch (e: SecurityException) {
    "Sicherung fehlgeschlagen: Keine Berechtigung, die Datei zu schreiben."
}

/** The message after "Vokabeln importieren". */
fun importResultText(import: BookImport): String {
    val result = import.counts
    val ignored = if (result.vocabularyIgnored > 0) " (davon ${result.vocabularyIgnored} ignoriert angelegt)" else ""
    val changes = listOfNotNull(
        result.vocabularyUpdated.takeIf { it > 0 }?.let { "$it geändert" },
        result.vocabularyExcluded.takeIf { it > 0 }?.let { "$it jetzt ignoriert" },
        result.vocabularyRestored.takeIf { it > 0 }?.let { "$it wieder aktiv" }
    )
    val changed = if (changes.isEmpty()) "" else " (davon ${changes.joinToString(", ")})"
    return BookTexts.importedInto(import.bookTitle, import.isNewBook) + " " +
        "${result.vocabularyImported} Vokabeln importiert$ignored, " +
        "${result.vocabularySkipped} schon vorhanden$changed, " +
        "${result.verbsAdded} unregelmäßige Verben hinzugefügt, " +
        "${result.verbsUpdated} Verben aktualisiert." +
        CoreWordTexts.ignoredSuffix(result.ignoredWithoutCoreWord)
}

/** Texts of "Sicherung wiederherstellen". */
object RestoreTexts {
    const val CONFIRM =
        "Der aktuelle Lernstand auf diesem Gerät wird vollständig durch die Sicherung ersetzt " +
            "(Bücher, Vokabeln, Lernlevel, Punkte, Avatare, Einstellungen). Das kann nicht rückgängig gemacht werden. " +
            "Das Beispielbuch ist nicht in der Sicherung und beginnt danach neu."

    /** One line per book of the file, never a number of books. */
    fun contents(info: BackupInfo): String =
        if (info.books.isEmpty()) {
            "Die Sicherung enthält kein Buch" + (if (info.hasSettings) ", nur Einstellungen." else ".")
        } else {
            "Die Sicherung enthält:\n" + info.books.joinToString("\n") { "• ${it.title}: ${counts(it)}" }
        }

    private fun counts(book: BackupBook): String = "${book.vocabulary} Vokabeln, ${book.verbs} Verben"

    /** Null if the file comes from the same app or does not say which app wrote it. */
    fun variantWarning(fileVariant: String?, debugApp: Boolean): String? = when (fileVariant) {
        VocTrainerJson.appVariant(debugApp) -> null
        VocTrainerJson.APP_VARIANT_DEBUG -> "Achtung: Diese Sicherung stammt aus der Test-App."
        VocTrainerJson.APP_VARIANT_RELEASE -> "Achtung: Diese Sicherung stammt aus der normalen App."
        else -> null
    }

    fun result(info: BackupInfo): String =
        "Sicherung wiederhergestellt: " +
            (info.books.joinToString(", ") { "${it.title} (${counts(it)})" }.ifEmpty { "kein Buch" }) +
            (if (info.hasSettings) ", Einstellungen übernommen." else ".") +
            CoreWordTexts.ignoredSuffix(info.ignoredWithoutCoreWord)
}

class VocabularyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabularyRepository(
        VocTrainerDatabase.getInstance(application), ExampleBook.loader(application), exampleBookAlwaysListed = BuildConfig.DEBUG
    )
    private val settings = AppSettings.getInstance(application)
    private val contentResolver = application.contentResolver

    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = _pendingRestore.asStateFlow()

    /** Whether an own book exists (not only the example book); decides which buttons are highlighted. */
    val hasOwnBook: StateFlow<Boolean> = repository.hasOwnBook
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The book list (see [VocabularyRepository.bookSummaries]); null until loaded. */
    val books: StateFlow<List<BookSummary>?> = repository.bookSummaries
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _pendingBookSwitch = MutableStateFlow<PendingBookSwitch?>(null)

    /** Asked after an import created a new book while another one is active. */
    val pendingBookSwitch: StateFlow<PendingBookSwitch?> = _pendingBookSwitch.asStateFlow()

    /** Makes another book active; nothing else changes. */
    fun selectBook(id: String) {
        viewModelScope.launch { repository.setActiveBook(id) }
    }

    /** "Ja": the new book becomes active. */
    fun confirmBookSwitch() {
        val pending = _pendingBookSwitch.value ?: return
        _pendingBookSwitch.value = null
        viewModelScope.launch {
            repository.setActiveBook(pending.bookId)
            _message.value = BookTexts.switched(pending.title)
        }
    }

    /** "Nein": the new book stays inactive. */
    fun declineBookSwitch() {
        _pendingBookSwitch.value = null
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** [onResult] receives null on success, otherwise the error to show in the form. */
    fun addVocabulary(input: ManualEntryInput, onResult: (ManualEntryResult.Invalid?) -> Unit) {
        when (val entry = parseManualEntry(input)) {
            is ManualEntryResult.Invalid -> onResult(entry)
            is ManualEntryResult.Valid -> viewModelScope.launch {
                if (repository.activeBookNow() == null) {
                    onResult(ManualEntryResult.Invalid(BookTexts.NO_BOOK_FOR_ENTRY))
                    return@launch
                }
                val added = repository.addVocabulary(entry.vocabulary)
                onResult(
                    if (added) null
                    else ManualEntryResult.Invalid("„${entry.vocabulary.english}“ gibt es in Unit ${entry.vocabulary.unit} schon.")
                )
            }
        }
    }

    fun importVocabulary(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val json = withContext(Dispatchers.IO) { readText(uri) }
                val import = repository.importVocabulary(json)
                if (import.counts.vocabularyImported > 0 && import.isActive) SelectionHint.request()
                // The question waits until the message is closed, so the learner first reads what was imported.
                if (import.isNewBook && !import.isActive) {
                    _pendingBookSwitch.value = PendingBookSwitch(import.bookId, import.bookTitle)
                }
                importResultText(import)
            } catch (e: InvalidFileException) {
                "Import fehlgeschlagen: ${e.message}"
            } catch (e: IOException) {
                "Import fehlgeschlagen: Die Datei konnte nicht gelesen werden."
            } catch (e: SecurityException) {
                "Import fehlgeschlagen: Keine Berechtigung, die Datei zu lesen."
            }
        }
    }

    fun exportSync(uri: Uri) {
        viewModelScope.launch {
            _message.value = writeBackup(contentResolver, uri, repository, settings) ?: BACKUP_SAVED
        }
    }

    /** "Sicherung wiederherstellen", step 1: reads and checks the file; nothing changes before [confirmRestore]. */
    fun chooseRestoreFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { readText(uri) }
                val info = withContext(Dispatchers.Default) { repository.backupInfo(json) }
                _pendingRestore.value = PendingRestore(json, info)
            } catch (e: InvalidFileException) {
                _message.value = "Wiederherstellen fehlgeschlagen: ${e.message}"
            } catch (e: IOException) {
                _message.value = "Wiederherstellen fehlgeschlagen: Die Datei konnte nicht gelesen werden."
            } catch (e: SecurityException) {
                _message.value = "Wiederherstellen fehlgeschlagen: Keine Berechtigung, die Datei zu lesen."
            }
        }
    }

    fun restoreWarning(info: BackupInfo): String? = RestoreTexts.variantWarning(info.appVariant, BuildConfig.DEBUG)

    /** Step 2, after the confirmation: replaces the learning state and the settings, then plans the reminder anew. */
    fun confirmRestore() {
        val pending = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            _message.value = try {
                val info = repository.restoreBackup(pending.json, settings)
                Reminder.schedule(getApplication())
                RestoreTexts.result(info)
            } catch (e: InvalidFileException) {
                "Wiederherstellen fehlgeschlagen: ${e.message}"
            }
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun readText(uri: Uri): String = readTextFile(contentResolver, uri)
}

/** Reads a chosen file as UTF-8 text (blocking); larger than [MAX_FILE_BYTES] is an [InvalidFileException]. */
internal fun readTextFile(contentResolver: ContentResolver, uri: Uri): String {
    val stream = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
    val output = ByteArrayOutputStream()
    stream.use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_FILE_BYTES) throw InvalidFileException("Die Datei ist zu groß.")
        }
    }
    return output.toString(Charsets.UTF_8.name())
}
