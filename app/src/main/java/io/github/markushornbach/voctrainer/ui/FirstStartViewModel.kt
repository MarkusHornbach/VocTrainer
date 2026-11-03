package io.github.markushornbach.voctrainer.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.InvalidFileException
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val LOG_TAG = "VocTrainer"

/** What the book choice of the first start led to; consumed by the screen. */
sealed interface BookChoiceResult {
    /** [bookId] is the active book now: on to the avatar choice ([example]: first the hint where the real book is loaded). */
    data class Chosen(val bookId: String, val example: Boolean = false) : BookChoiceResult

    /** The chosen file could not be imported: stay on the book choice. */
    data object Invalid : BookChoiceResult
}

/** The book choice of the first start ("Beispielbuch ausprobieren", "Mein Buch laden"). */
class FirstStartViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabularyRepository(VocTrainerDatabase.getInstance(application), ExampleBook.loader(application))
    private val settings = AppSettings.getInstance(application)
    private val contentResolver = application.contentResolver

    private val _result = MutableStateFlow<BookChoiceResult?>(null)
    val result: StateFlow<BookChoiceResult?> = _result.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** A file is being imported: the cards wait. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** "Beispielbuch ausprobieren": the example book becomes active (it is added at every start). */
    fun chooseExampleBook() {
        choose {
            repository.setActiveBook(ExampleBook.ID)
            ExampleBook.ID
        }
    }

    /**
     * "Mein Buch laden": imports [uri] with the normal import and makes the book of the file active,
     * without the "switch book?" question and without the import message.
     */
    fun loadBook(uri: Uri) {
        choose {
            val json = withContext(Dispatchers.IO) { readTextFile(contentResolver, uri) }
            val import = repository.importVocabulary(json)
            repository.setActiveBook(import.bookId)
            Log.i(LOG_TAG, "First start: book ${import.bookId} loaded, ${import.counts.vocabularyImported} entries")
            import.bookId
        }
    }

    private fun choose(action: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _result.value = try {
                val bookId = action()
                // The avatar choice belongs to the vocabulary mode.
                settings.setQuizMode(QuizMode.VOCABULARY)
                BookChoiceResult.Chosen(bookId, example = bookId == ExampleBook.ID)
            } catch (e: InvalidFileException) {
                Log.w(LOG_TAG, "First start: file not imported: ${e.message}")
                BookChoiceResult.Invalid
            } catch (e: IOException) {
                Log.w(LOG_TAG, "First start: file could not be read", e)
                BookChoiceResult.Invalid
            } catch (e: SecurityException) {
                Log.w(LOG_TAG, "First start: no permission to read the file", e)
                BookChoiceResult.Invalid
            } finally {
                _busy.value = false
            }
        }
    }

    fun consumeResult() {
        _result.value = null
    }
}
