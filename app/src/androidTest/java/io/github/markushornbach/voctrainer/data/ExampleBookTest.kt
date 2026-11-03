package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The built-in example book "Beispielbuch Englisch" (see [VocabularyRepository.ensureExampleBook]). */
@RunWith(AndroidJUnit4::class)
class ExampleBookTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val SETTINGS = "test_example_book_settings"
        const val DEVICE = "test_example_book_device"
        const val OTHER = "testbuch-6"
        const val DAY = "2026-10-09"
        const val PHONE = "phone"
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = Room.inMemoryDatabaseBuilder(context, VocTrainerDatabase::class.java).build()
        repository = VocabularyRepository(db, ExampleBook.loader(context))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    private suspend fun exampleVocabulary() = db.vocabularyDao().getAllOfBookIncludingDeleted(ExampleBook.ID)

    private suspend fun exampleVerbs() = db.irregularVerbDao().getAllOfBookIncludingDeleted(ExampleBook.ID)

    private suspend fun importOtherBook() = repository.importVocabulary(
        """{"format": "voctrainer-import", "version": 2, "bookId": "$OTHER", "bookTitle": "Klasse 6",
            "entries": [{"english": "dog", "german": ["Hund"], "unit": 1, "coreWord": "dog"}]}"""
    )

    @Test
    fun emptyDatabaseGetsTheActiveExampleBook() = runBlocking {
        val import = repository.ensureExampleBook()!!

        assertTrue(import.isNewBook && import.isActive)
        val book = repository.activeBookNow()!!
        assertEquals(ExampleBook.ID, book.id)
        assertEquals("Beispielbuch Englisch", book.title)
        assertEquals(150, exampleVocabulary().count { !it.isDeleted })
        assertEquals(36, exampleVerbs().count { !it.isDeleted })
        assertTrue(exampleVocabulary().all { it.coreWord != null })
    }

    @Test
    fun anotherActiveBookStaysActive() = runBlocking {
        importOtherBook()

        val import = repository.ensureExampleBook()!!

        assertTrue(import.isNewBook)
        assertFalse(import.isActive)
        assertEquals(OTHER, repository.activeBookNow()!!.id)
        assertFalse(db.bookDao().getById(ExampleBook.ID)!!.isActive)
        assertEquals(150, exampleVocabulary().size)
    }

    @Test
    fun runningItAgainChangesNothing() = runBlocking {
        repository.ensureExampleBook()
        val ids = exampleVocabulary().take(5).map { it.id }
        repository.setVocabularyLevels(ids, 3)
        repository.setIgnored(ids.last(), true)
        val shelter = exampleVocabulary().first { it.english == "animal shelter" }
        repository.update(shelter.copy(coreWord = "animal"))
        val before = exampleVocabulary()
        val verbsBefore = exampleVerbs()

        val again = repository.ensureExampleBook()!!

        assertFalse(again.isNewBook)
        assertEquals(VocabularyImportResult(0, 150, 0, 0), again.counts)
        assertEquals(before, exampleVocabulary())
        assertEquals(verbsBefore, exampleVerbs())
        assertEquals(listOf(3, 3, 3, 3, 3), exampleVocabulary().filter { it.id in ids }.map { it.leitnerLevel })
        assertEquals(1, db.bookDao().getAll().size)
    }

    /** The example book with progress in every table: levels, points, a round, an avatar and a badge. */
    private suspend fun exampleBookWithProgress() {
        repository.ensureExampleBook()
        repository.setActiveBook(ExampleBook.ID)
        repository.setVocabularyLevels(exampleVocabulary().take(3).map { it.id }, 4)
        repository.setVerbLevels(exampleVerbs().take(2).map { it.id }, 2)
        repository.addTestPoints(DAY, PHONE, 40)
        repository.recordRoundCompleted(DAY, PHONE, 1)
        repository.chooseFigure(FigureKind.DRAGON)
        db.badgeDao().upsertAll(listOf(Badge("streak", 1, 5L, ExampleBook.ID)))
    }

    /** A file of every book, the example book included: what the backup must leave out. */
    private suspend fun fileWithTheExampleBook(): String = VocTrainerJson.encodeSync(
        db.bookDao().getAll(),
        db.vocabularyDao().getAllBooksIncludingDeletedInInsertionOrder(), db.irregularVerbDao().getAllBooksIncludingDeletedInInsertionOrder(),
        db.practiceDayDao().getAllBooks(), db.pointsDayDao().getAllBooks(), db.figureDao().getAllBooks(), db.badgeDao().getAllBooks()
    )

    /** Every bookId (and book id) in every table of the file. */
    private fun bookIdsIn(file: String): Map<String, Set<String>> {
        val root = Json.parseToJsonElement(file).jsonObject
        return listOf("books", "entries", "irregularVerbs", "practiceDays", "pointsDays", "figures", "badges").associateWith { table ->
            val key = if (table == "books") "id" else "bookId"
            root.getValue(table).jsonArray.map { it.jsonObject.getValue(key).jsonPrimitive.content }.toSet()
        }
    }

    @Test
    fun theBackupContainsNothingOfTheExampleBook() = runBlocking {
        importOtherBook()
        exampleBookWithProgress()
        // All tables really have rows of the example book.
        assertTrue(bookIdsIn(fileWithTheExampleBook()).values.all { ExampleBook.ID in it })

        val backup = repository.exportSync()

        val ids = bookIdsIn(backup)
        assertEquals(setOf(OTHER), ids.getValue("books"))
        assertEquals(setOf(OTHER), ids.getValue("entries"))
        for ((table, books) in ids) assertFalse(table, ExampleBook.ID in books)
        // The example book was active: the file has no active book, the restore makes the first one active.
        val info = repository.backupInfo(backup)
        assertEquals(listOf(BackupBook("Klasse 6", 1, 0)), info.books)
    }

    @Test
    fun aBackupWithOnlyTheExampleBookIsValidAndRestoresWithoutError() = runBlocking {
        exampleBookWithProgress()
        val settings = AppSettings(context, SETTINGS, DEVICE)

        val backup = repository.exportSync(settings.toBackup())

        assertTrue(bookIdsIn(backup).values.all { it.isEmpty() })
        val info = repository.backupInfo(backup)
        assertTrue(info.books.isEmpty())
        assertTrue(info.hasSettings)
        repository.restoreBackup(backup, settings)
        // Nothing else there: the fresh example book is active again.
        assertEquals(ExampleBook.ID, repository.activeBookNow()!!.id)
        assertTrue(exampleVocabulary().all { it.leitnerLevel == 0 })
        assertEquals(0, repository.totalPointsNow())
    }

    @Test
    fun theBookListShowsTheExampleBookOnlyWhileItIsActive() = runBlocking {
        importOtherBook()
        repository.ensureExampleBook()
        assertEquals(listOf(OTHER), repository.bookSummaries.first().map { it.book.id })

        repository.setActiveBook(ExampleBook.ID)
        assertEquals(listOf(OTHER, ExampleBook.ID), repository.bookSummaries.first().map { it.book.id })

        repository.setActiveBook(OTHER)
        assertEquals(listOf(OTHER), repository.bookSummaries.first().map { it.book.id })
    }

    @Test
    fun restoreOfABackupWithoutTheExampleBookBringsItBackInactive() = runBlocking {
        importOtherBook()
        val backup = repository.exportSync()
        repository.ensureExampleBook()
        val settings = AppSettings(context, SETTINGS, DEVICE)

        repository.restoreBackup(backup, settings)

        assertEquals(OTHER, repository.activeBookNow()!!.id)
        val example = db.bookDao().getById(ExampleBook.ID)!!
        assertFalse(example.isActive)
        assertEquals(150, exampleVocabulary().size)
        assertEquals(36, exampleVerbs().size)
    }
}
