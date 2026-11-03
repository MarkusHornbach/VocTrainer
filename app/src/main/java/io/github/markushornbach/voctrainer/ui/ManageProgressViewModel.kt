package io.github.markushornbach.voctrainer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The dialog of "Buch neu beginnen" that is open. */
enum class RestartStep {
    NONE,

    /**
     * "Buch neu beginnen?": what is reset and what stays; next comes the backup. For the example
     * book (never in a backup) there is no backup step: this dialog restarts it directly.
     */
    ASK,

    /** "Wirklich neu beginnen?" after the backup was saved. */
    CONFIRM,

    /** Test app only: "Wirklich neu beginnen?" without a backup. */
    CONFIRM_WITHOUT_BACKUP
}

/**
 * [bookTitle] null = no book; [canRestart] see [io.github.markushornbach.voctrainer.data.BookRestart.hasSomethingToReset];
 * [isExampleBook]: the active book is the example book.
 */
data class ManageProgressState(
    val loaded: Boolean = false,
    val bookTitle: String? = null,
    val canRestart: Boolean = false,
    val isExampleBook: Boolean = false
)

/**
 * The card "Buch neu beginnen" on "Vokabeln verwalten" for the active book, only after a backup
 * was saved with the same save dialog and content as "Sicherung exportieren" ([writeBackup]).
 * The example book is restarted without a backup, since a backup never contains it.
 */
class ManageProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabularyRepository(VocTrainerDatabase.getInstance(application))
    private val settings = AppSettings.getInstance(application)
    private val contentResolver = application.contentResolver

    val state: StateFlow<ManageProgressState> = combine(repository.activeBook, repository.canRestartBook) { book, can ->
        ManageProgressState(loaded = true, bookTitle = book?.title, canRestart = can, isExampleBook = book?.id == ExampleBook.ID)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ManageProgressState())

    private val _step = MutableStateFlow(RestartStep.NONE)
    val step: StateFlow<RestartStep> = _step.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** Snackbar on this page: cancelled, backup failed, or the book starts anew. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun askRestart() {
        if (state.value.canRestart) _step.value = RestartStep.ASK
    }

    /** "Sicherung speichern": the dialog closes while the save dialog is open. */
    fun startBackup() {
        _step.value = RestartStep.NONE
    }

    /** The save dialog returned: null = cancelled; otherwise the backup is written, then comes the last question. */
    fun backupChosen(uri: Uri?) {
        if (uri == null) {
            _notice.value = RestartBookTexts.CANCELLED
            return
        }
        viewModelScope.launch {
            val error = writeBackup(contentResolver, uri, repository, settings)
            if (error == null) _step.value = RestartStep.CONFIRM else _notice.value = error
        }
    }

    /** Test app only: "🧪 Ohne Sicherung". */
    fun skipBackupForTest() {
        if (BuildConfig.DEBUG) _step.value = RestartStep.CONFIRM_WITHOUT_BACKUP
    }

    /** "Abbrechen" or dismissed: nothing is reset. */
    fun cancel() {
        val asked = _step.value != RestartStep.NONE
        _step.value = RestartStep.NONE
        if (asked) _notice.value = RestartBookTexts.CANCELLED
    }

    /** "Endgültig neu beginnen"; for the example book already in the first dialog. */
    fun confirmRestart() {
        val step = _step.value
        val confirmed = step == RestartStep.CONFIRM || step == RestartStep.CONFIRM_WITHOUT_BACKUP ||
            (step == RestartStep.ASK && state.value.isExampleBook)
        if (!confirmed) return
        _step.value = RestartStep.NONE
        viewModelScope.launch {
            repository.restartBook(settings)?.let { _notice.value = RestartBookTexts.restarted(it) }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }
}

/** German texts of "Buch neu beginnen" (section at the bottom of "Vokabeln verwalten"). */
object RestartBookTexts {
    const val SECTION_TITLE = "Lernstand zurücksetzen"
    const val TITLE = "Buch neu beginnen"

    const val NOTHING_TO_RESET = "In diesem Buch gibt es noch nichts zurückzusetzen."

    const val ASK_TITLE = "Buch neu beginnen?"

    /** [isExampleBook]: no backup step, and the old state cannot be brought back. */
    fun askText(bookTitle: String, isExampleBook: Boolean = false): String =
        "„$bookTitle“ beginnt noch einmal von vorn.\n\n" +
            "Zurückgesetzt werden: alle Wörter und Verben auf „neu“, Punkte, Abzeichen, Serie und der " +
            "wachsende Avatar (du wählst danach einen neuen).\n\n" +
            "Es bleiben: deine fertigen Avatare, die Vokabeln, die Auswahl, andere Bücher und die Einstellungen.\n\n" +
            (if (isExampleBook) EXAMPLE_BOOK_LOST else BACKUP_FIRST) + "\n\n" +
            "Gilt nur für dieses Gerät."

    const val BACKUP_FIRST = "Zuerst wird eine Sicherung gespeichert. Nur über diese Sicherung lässt sich der alte Stand zurückholen."
    const val EXAMPLE_BOOK_LOST = "Der alte Stand im Beispielbuch lässt sich danach nicht zurückholen."

    const val SAVE_BACKUP = "Sicherung speichern"
    const val WITHOUT_BACKUP = "Ohne Sicherung"
    const val CANCEL = "Abbrechen"

    const val CONFIRM_TITLE = "Wirklich neu beginnen?"
    const val CONFIRM_TEXT = "Die Sicherung ist gespeichert. Das kann nur über diese Sicherung rückgängig gemacht werden."
    const val CONFIRM_TEXT_WITHOUT_BACKUP = "Es wurde keine Sicherung gespeichert. Das kann nicht rückgängig gemacht werden."
    const val CONFIRM = "Endgültig neu beginnen"

    const val CANCELLED = "Abgebrochen – es wurde nichts zurückgesetzt."

    fun restarted(bookTitle: String): String = "„$bookTitle“ beginnt neu."
}
