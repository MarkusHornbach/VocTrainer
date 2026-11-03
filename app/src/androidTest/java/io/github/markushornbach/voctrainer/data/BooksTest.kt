package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Several books: import into a named book, switching, per-book progress, sync and restore with two books. */
@RunWith(AndroidJUnit4::class)
class BooksTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val DAY = "2026-10-09"
        const val DEVICE = "device-a"
        const val SIXTH = "testbuch-6"
        const val SEVENTH = "englisch-klasse-7"
        const val SETTINGS = "test_books_settings"
        const val DEVICE_PREFS = "test_books_device"
    }

    /** An import file of [bookId] with [words] (English = core word) in Unit 1 and one verb. */
    private fun importFile(bookId: String, title: String, vararg words: String, verb: String = "(to) go") = """
        {"format": "voctrainer-import", "version": 2, "bookId": "$bookId", "bookTitle": "$title",
         "entries": [${words.joinToString(",") { """{"english": "$it", "german": ["${it}_de"], "unit": 1, "coreWord": "$it"}""" }}],
         "irregularVerbs": [{"infinitive": "$verb", "simplePast": "x", "german": ["v"], "unit": 1}]}
    """.trimIndent()

    private fun newDatabase() = Room.inMemoryDatabaseBuilder(context, VocTrainerDatabase::class.java).build()

    @Before
    fun setUp() {
        db = newDatabase()
        repository = VocabularyRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE_PREFS)
    }

    private suspend fun shownWords() = repository.vocabularyInInsertionOrder.first().map { it.english }

    @Test
    fun withoutABookNothingIsShownAndTheFirstImportCreatesTheActiveBook() = runBlocking {
        assertNull(repository.activeBookNow())
        assertTrue(shownWords().isEmpty())
        // Without a book nothing can be stored.
        assertFalse(repository.addVocabulary(Vocabulary(english = "dog", germanMeanings = listOf("Hund"), unit = 1, coreWord = "dog")))

        val import = repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog", "cat"))

        assertTrue(import.isNewBook)
        assertTrue(import.isActive)
        assertEquals(Book(SIXTH, "Klasse 6", "en", isActive = true), repository.activeBookNow())
        assertEquals(listOf("dog", "cat"), shownWords())
        assertEquals(2, repository.activeVocabularyCount.first())
    }

    @Test
    fun importOfANewBookKeepsItInactiveUntilTheLearnerSwitches() = runBlocking {
        repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog", "cat"))

        val import = repository.importVocabulary(importFile(SEVENTH, "Klasse 7", "dog", "bird", "fish"))

        assertTrue(import.isNewBook)
        assertFalse(import.isActive)
        // The same word in another book is no duplicate: it belongs to that book.
        assertEquals(3, import.counts.vocabularyImported)
        assertEquals(SIXTH, repository.activeBookNow()!!.id)
        assertEquals(listOf("dog", "cat"), shownWords())

        // "Ja": switching only changes what is shown.
        repository.setActiveBook(SEVENTH)
        assertEquals(listOf("dog", "bird", "fish"), shownWords())
        assertEquals(3, repository.activeVocabularyCount.first())
        assertEquals(listOf(SIXTH to false, SEVENTH to true), db.bookDao().getAll().map { it.id to it.isActive })
        assertEquals(5, db.vocabularyDao().getAllBooksIncludingDeleted().size)
    }

    @Test
    fun importIntoAnInactiveBookMergesThereAndTakesOverItsTitle() = runBlocking {
        repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog"))
        repository.importVocabulary(importFile(SEVENTH, "Klasse 7", "bird"))

        val again = repository.importVocabulary(importFile(SEVENTH, "Englisch Klasse 7", "bird", "fish"))

        assertFalse(again.isNewBook)
        assertFalse(again.isActive)
        assertEquals("Englisch Klasse 7", again.bookTitle)
        assertEquals(1, again.counts.vocabularyImported)
        assertEquals(1, again.counts.vocabularySkipped)
        assertEquals("Englisch Klasse 7", db.bookDao().getById(SEVENTH)!!.title)
        assertEquals(listOf("dog"), shownWords())
        assertEquals(listOf("bird", "fish"), db.vocabularyDao().getAllOfBookIncludingDeleted(SEVENTH).map { it.english })
    }

    @Test
    fun bookListCountsWordsAndLearnedWordsPerBook() = runBlocking {
        repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog", "cat", "cow"))
        repository.importVocabulary(importFile(SEVENTH, "Klasse 7", "bird"))
        val dog = db.vocabularyDao().getAllOfBookIncludingDeleted(SIXTH).first { it.english == "dog" }
        db.vocabularyDao().update(dog.copy(leitnerLevel = 4))
        repository.setIgnored(db.vocabularyDao().getAllOfBookIncludingDeleted(SIXTH).first { it.english == "cow" }.id, true)

        val summaries = repository.bookSummaries.first()

        assertEquals(listOf(SIXTH, SEVENTH), summaries.map { it.book.id })
        assertEquals(listOf(2, 1), summaries.map { it.words })
        assertEquals(listOf(1, 0), summaries.map { it.learnedWords })
        assertEquals(listOf(true, false), summaries.map { it.book.isActive })
    }

    @Test
    fun pointsFigureStreakBadgesAndSelectionBelongToTheBook() = runBlocking {
        repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog"))
        repository.importVocabulary(importFile(SEVENTH, "Klasse 7", "bird"))
        repository.addTestPoints(DAY, DEVICE, 150)
        repository.recordRoundCompleted(DAY, DEVICE, 2)
        repository.chooseFigure(FigureKind.DRAGON)
        db.badgeDao().insertNew(listOf(Badge("streak", 1, 5L, SIXTH)))
        repository.saveQuizFilter(QuizMode.VOCABULARY, QuizFilter(UnitChoice.Number(1)))
        assertEquals(150, repository.totalPointsNow())
        // A book of one entry asks for a tenth of a full avatar (3, 12, 30, 55, 90, 130 points): stage 6.
        assertEquals(6, db.figureDao().getAll().single().stage)

        repository.setActiveBook(SEVENTH)

        assertEquals(0, repository.totalPointsNow())
        assertTrue(repository.figures.first().isEmpty())
        assertTrue(repository.allPracticeDays().isEmpty())
        assertEquals(0, repository.badgeCount())
        assertNull(repository.loadQuizFilter(QuizMode.VOCABULARY))
        // A word of this book earns its points here.
        val bird = repository.vocabularyInInsertionOrder.first().single()
        repository.recordFirstAttempt(bird.id, correct = true, early = false, date = DAY, deviceId = DEVICE)
        assertEquals(1, repository.totalPointsNow())

        repository.setActiveBook(SIXTH)
        assertEquals(150, repository.totalPointsNow())
        assertEquals(1, repository.allPracticeDays().single().roundsCompleted)
        assertEquals(1, repository.badgeCount())
        assertEquals(QuizFilter(UnitChoice.Number(1)), repository.loadQuizFilter(QuizMode.VOCABULARY))
    }

    @Test
    fun backupWithTwoBooksRestoresEverythingIncludingTheActiveBook() = runBlocking {
        val settings = AppSettings(context, SETTINGS, DEVICE_PREFS)
        repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog", "cat"))
        repository.importVocabulary(importFile(SEVENTH, "Klasse 7", "bird"))
        val dog = db.vocabularyDao().getAllOfBookIncludingDeleted(SIXTH).first { it.english == "dog" }
        repository.saveQuizFilter(QuizMode.VOCABULARY, QuizFilter(UnitChoice.Number(1)))
        db.vocabularyDao().update(dog.copy(leitnerLevel = 5, highestLevel = 5))
        repository.addTestPoints(DAY, DEVICE, 50)
        repository.chooseFigure(FigureKind.DRAGON)
        repository.setActiveBook(SEVENTH)
        repository.addTestPoints(DAY, DEVICE, 7)
        val books = db.bookDao().getAll()
        val vocabulary = db.vocabularyDao().getAllBooksIncludingDeletedInInsertionOrder()
        val verbs = db.irregularVerbDao().getAllBooksIncludingDeletedInInsertionOrder()
        val points = db.pointsDayDao().getAllBooks().toSet()
        val figures = db.figureDao().getAllBooks()
        val exported = repository.exportSync(settings.toBackup())
        assertEquals(
            BackupInfo(listOf(BackupBook("Klasse 6", 2, 1), BackupBook("Klasse 7", 1, 1)), hasSettings = true, appVariant = "debug"),
            repository.backupInfo(exported)
        )

        // Afterwards everything changes: other active book, new book, other levels and points.
        repository.setActiveBook(SIXTH)
        repository.importVocabulary(importFile("third", "Drittes Buch", "sun"))
        repository.setVocabularyLevels(listOf(dog.id), 0)
        repository.addTestPoints(DAY, DEVICE, 100)

        val info = repository.restoreBackup(exported, settings)

        assertEquals(listOf("Klasse 6", "Klasse 7"), info.books.map { it.title })
        assertEquals(books, db.bookDao().getAll())
        assertEquals(SEVENTH, repository.activeBookNow()!!.id)
        assertEquals(vocabulary, db.vocabularyDao().getAllBooksIncludingDeletedInInsertionOrder())
        assertEquals(verbs, db.irregularVerbDao().getAllBooksIncludingDeletedInInsertionOrder())
        assertEquals(points, db.pointsDayDao().getAllBooks().toSet())
        assertEquals(figures, db.figureDao().getAllBooks())
        assertEquals(listOf("bird"), shownWords())
        assertEquals(7, repository.totalPointsNow())
        repository.setActiveBook(SIXTH)
        assertEquals(50, repository.totalPointsNow())
        assertEquals(QuizFilter(UnitChoice.Number(1)), repository.loadQuizFilter(QuizMode.VOCABULARY))
        // The restored stage of the other book counts as shown, too.
        val figure = db.figureDao().getAll().single()
        assertEquals(figure.stage, settings.lastShownFigureStage(SIXTH, figure.slot))
    }

    @Test
    fun theTestBookIsCreatedOnceAndInactive() = runBlocking {
        repository.importVocabulary(importFile(SIXTH, "Klasse 6", "dog"))

        assertTrue(repository.createTestBookForTest())
        assertFalse(repository.createTestBookForTest())

        assertEquals(SIXTH, repository.activeBookNow()!!.id)
        val testBook = db.bookDao().getById(VocabularyRepository.TEST_BOOK_ID)!!
        assertEquals("Testbuch", testBook.title)
        assertFalse(testBook.isActive)
        val words = db.vocabularyDao().getAllOfBookIncludingDeleted(testBook.id)
        assertEquals(10, words.size)
        assertTrue(words.all { it.coreWord != null && !it.isDeleted })
        assertEquals(2, db.irregularVerbDao().getAllOfBookIncludingDeleted(testBook.id).size)
        // Every word stays active after the core-word check of the app start.
        assertEquals(0, repository.ensureCoreWords())
    }
}
