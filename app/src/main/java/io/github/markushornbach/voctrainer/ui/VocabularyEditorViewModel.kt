package io.github.markushornbach.voctrainer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.toQuizWord
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditorListState(
    val loaded: Boolean = false,
    val filter: EditorFilter = EditorFilter(),
    val unitChoices: List<UnitChoice> = listOf(UnitChoice.All),
    val partOptions: List<String?> = emptyList(),
    val pages: List<Int> = emptyList(),
    val entries: List<Vocabulary> = emptyList()
)

data class EditState(
    val original: Vocabulary,
    val input: EditInput,
    val validation: EditValidation,
    /** Highest selectable level; above the stored level only in admin mode. */
    val maxLevel: Int,
    /** Field errors appear after the first save attempt; the duplicate hint appears at once. */
    val showErrors: Boolean = false
) {
    val hasChanges: Boolean get() = input != EditInput.of(original)

    /** An ignored entry without a usable core word: saving restores it together with the chosen word. */
    val restoring: Boolean get() = original.isDeleted
}

/** A snackbar text; [undoDeleteId] is set for "Vokabel gelöscht" with "Rückgängig". */
data class EditorMessage(val text: String, val undoDeleteId: String? = null)

/** List and edit page of "Vokabeln editieren"; activity-scoped, so the filters survive the edit page. */
class VocabularyEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabularyRepository(VocTrainerDatabase.getInstance(application))
    private val settings = AppSettings.getInstance(application)

    private val all: StateFlow<List<Vocabulary>?> = repository.allVocabularyIncludingIgnored
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val chosenFilter = MutableStateFlow(EditorFilter())

    /** Admin mode (the test app): the level can also be raised by hand. */
    val adminMode: StateFlow<Boolean> = settings.adminMode

    private val _filtersExpanded = MutableStateFlow(false)
    val filtersExpanded: StateFlow<Boolean> = _filtersExpanded.asStateFlow()

    val listState: StateFlow<EditorListState> = combine(all, chosenFilter) { list, chosen ->
        if (list == null) return@combine EditorListState(filter = chosen)
        val filter = VocabularyEditor.normalize(list, chosen)
        val words = list.map { it.toQuizWord() }
        EditorListState(
            loaded = true,
            filter = filter,
            unitChoices = QuizSelection.unitChoices(words),
            partOptions = QuizSelection.partOptions(words, filter.selection.unit),
            pages = QuizSelection.availablePages(words, filter.selection),
            entries = VocabularyEditor.filter(list, filter)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EditorListState())

    private val _editing = MutableStateFlow<EditState?>(null)
    val editing: StateFlow<EditState?> = _editing.asStateFlow()

    private val _message = MutableStateFlow<EditorMessage?>(null)
    val message: StateFlow<EditorMessage?> = _message.asStateFlow()

    fun toggleFilters() {
        _filtersExpanded.value = !_filtersExpanded.value
    }

    fun setSearch(text: String) = updateFilter { it.copy(search = text) }

    fun selectUnit(unit: UnitChoice) = updateSelection { QuizSelection.withUnit(it, unit) }

    fun togglePart(part: String?) = updateSelection { QuizSelection.togglePart(it, part, listState.value.partOptions) }

    fun selectAllParts() = updateSelection(QuizSelection::withAllParts)

    fun selectPageFrom(page: Int?) = updateSelection { QuizSelection.withPageFrom(it, page) }

    fun selectPageTo(page: Int?) = updateSelection { it.copy(pageTo = page) }

    fun toggleLevel(level: Int) = updateFilter { it.copy(levels = it.levels.toggle(level)) }

    fun toggleType(type: WordType) = updateFilter { it.copy(types = it.types.toggle(type)) }

    fun setShowIgnored(show: Boolean) = updateFilter { it.copy(showIgnored = show) }

    /**
     * Opening "Vokabeln editieren": Unit/Part/pages start with the quiz's vocabulary selection
     * (also while the quiz is in the verb mode), the other filters empty. Changes here do not
     * touch the quiz selection. The selection is the one saved in the active book.
     */
    fun startWithQuizSelection() {
        chosenFilter.value = EditorFilter()
        viewModelScope.launch {
            chosenFilter.value = VocabularyEditor.startFilter(repository.loadQuizFilter(QuizMode.VOCABULARY))
        }
    }

    fun resetFilters() {
        chosenFilter.value = EditorFilter()
    }

    // Builds on the normalized filter so stale selections never come back.
    private fun updateFilter(change: (EditorFilter) -> EditorFilter) {
        chosenFilter.value = change(listState.value.filter.takeIf { listState.value.loaded } ?: chosenFilter.value)
    }

    private fun updateSelection(change: (QuizFilter) -> QuizFilter) =
        updateFilter { it.copy(selection = change(it.selection)) }

    /** Prepares the edit page for [entry]. */
    fun startEditing(entry: Vocabulary) {
        val input = EditInput.of(entry)
        _editing.value = EditState(
            entry, input, validate(input, entry),
            maxLevel = VocabularyEditor.maxManualLevel(entry, settings.adminMode.value)
        )
    }

    /** Like [startEditing] for the word with [id]; [onMissing] if it does not exist any more. */
    fun startEditingById(id: String, onMissing: () -> Unit, onReady: () -> Unit) {
        viewModelScope.launch {
            val entry = repository.vocabularyById(id)
            if (entry == null) {
                onMissing()
            } else {
                startEditing(entry)
                onReady()
            }
        }
    }

    fun updateInput(input: EditInput) {
        val current = _editing.value ?: return
        _editing.value = current.copy(input = input, validation = validate(input, current.original))
    }

    /** [onDone] runs after a successful save; with errors the form shows them instead. */
    fun save(onDone: () -> Unit) {
        val current = _editing.value ?: return
        val validation = validate(current.input, current.original)
        val updated = validation.updated
        if (updated == null) {
            _editing.value = current.copy(validation = validation, showErrors = true)
            return
        }
        viewModelScope.launch {
            when {
                current.restoring -> {
                    repository.update(updated.copy(isDeleted = false))
                    _message.value = EditorMessage("„${updated.english}“ wiederhergestellt")
                }
                // An automatically chosen core word is also a change (the stored one was missing or outdated).
                current.hasChanges || updated.coreWord != current.original.coreWord -> repository.update(updated)
            }
            _editing.value = null
            onDone()
        }
    }

    fun stopEditing() {
        _editing.value = null
    }

    /** Ignores the edited entry and leaves the edit page; the list shows an undo snackbar. */
    fun deleteEditing(onDone: () -> Unit) {
        val current = _editing.value ?: return
        viewModelScope.launch {
            repository.setIgnored(current.original.id, ignored = true)
            _editing.value = null
            _message.value = EditorMessage("Vokabel gelöscht", undoDeleteId = current.original.id)
            onDone()
        }
    }

    fun undoDelete(id: String) {
        viewModelScope.launch { repository.setIgnored(id, ignored = false) }
    }

    /**
     * "Wiederherstellen" in the list. Returns false (and restores nothing) if the entry has no usable
     * core word: the caller then opens the edit page, which restores it together with a chosen word.
     */
    fun restore(entry: Vocabulary): Boolean {
        if (VocabularyEditor.needsCoreWordToRestore(entry)) return false
        viewModelScope.launch {
            repository.setIgnored(entry.id, ignored = false)
            _message.value = EditorMessage("„${entry.english}“ wiederhergestellt")
        }
        return true
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun validate(input: EditInput, original: Vocabulary): EditValidation =
        VocabularyEditor.validate(input, original, all.value.orEmpty(), settings.adminMode.value)

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}
