package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Re-importing a book file corrects the existing entries; the progress stays (see [VocabularyImporter.plan]). */
@RunWith(AndroidJUnit4::class)
class ImportAsReferenceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val BOOK = "testbuch-6"
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, VocTrainerDatabase::class.java).build()
        repository = VocabularyRepository(db, ExampleBook.loader(context))
    }

    @After
    fun tearDown() = db.close()

    private fun bookFile(vararg entries: String) =
        """{"format": "voctrainer-import", "version": 2, "bookId": "$BOOK", "bookTitle": "Klasse 6",
            "entries": [${entries.joinToString(",")}]}"""

    private val dog = """{"english": "dog", "german": ["Hund"], "unit": 1, "page": 10, "coreWord": "dog"}"""
    private val cat = """{"english": "cat", "german": ["Katze"], "unit": 1, "page": 10, "coreWord": "cat"}"""
    private val cow = """{"english": "cow", "german": ["Kuh"], "unit": 1, "page": 11, "coreWord": "cow"}"""
    private val pig = """{"english": "pig", "german": ["Schwein"], "unit": 1, "page": 11, "coreWord": "pig"}"""

    private suspend fun words() = db.vocabularyDao().getAllOfBookIncludingDeleted(BOOK).associateBy { it.english }

    @Test
    fun reImportUpdatesIgnoresAndRestoresEntriesButKeepsTheProgress() = runBlocking {
        repository.importVocabulary(bookFile(dog, cat, cow, pig))
        val dao = db.vocabularyDao()
        words().values.forEachIndexed { index, word ->
            dao.update(word.copy(leitnerLevel = index + 1, highestLevel = index + 2, lastReviewedAt = 1_000L + index))
        }
        repository.setIgnored(words().getValue("pig").id, ignored = true)
        val before = words()

        val result = repository.importVocabulary(
            bookFile(
                dog.replace("[\"Hund\"]", "[\"Hund\", \"Rüde\"]"),
                cat.replace("\"page\": 10", "\"page\": 12"),
                cow.replace("{", "{\"excluded\": true, "),
                pig
            )
        ).counts

        assertEquals(
            VocabularyImportResult(0, 4, 0, 0, vocabularyCompleted = 1, vocabularyUpdated = 2, vocabularyExcluded = 1, vocabularyRestored = 1),
            result
        )
        val after = words()
        assertEquals(listOf("Hund", "Rüde"), after.getValue("dog").germanMeanings)
        assertEquals(12, after.getValue("cat").page)
        assertTrue(after.getValue("cow").isDeleted)
        assertFalse(after.getValue("pig").isDeleted)
        assertEquals(before.mapValues { it.value.id }, after.mapValues { it.value.id })
        assertEquals(before.mapValues { it.value.leitnerLevel }, after.mapValues { it.value.leitnerLevel })
        assertEquals(before.mapValues { it.value.highestLevel }, after.mapValues { it.value.highestLevel })
        assertEquals(before.mapValues { it.value.lastReviewedAt }, after.mapValues { it.value.lastReviewedAt })
    }

    @Test
    fun theExampleBookStartsUnchangedAfterARestart() = runBlocking {
        repository.ensureExampleBook()
        val dao = db.vocabularyDao()
        val words = dao.getAllOfBookIncludingDeleted(ExampleBook.ID)
        repository.setVocabularyLevels(words.take(3).map { it.id }, 4)
        repository.setIgnored(words[3].id, ignored = true)
        repository.update(dao.getById(words[4].id)!!.copy(germanMeanings = listOf("eigene Bedeutung"), page = 1))
        val shelter = words.first { it.english == "animal shelter" }
        repository.update(dao.getById(shelter.id)!!.copy(coreWord = "animal"))
        repository.importVocabulary(bookFile(dog))
        val before = dao.getAllOfBookIncludingDeleted(ExampleBook.ID)

        // A new start: a new repository applies the example book again.
        val again = VocabularyRepository(db, ExampleBook.loader(context)).ensureExampleBook()!!

        assertEquals(VocabularyImportResult(0, 150, 0, 0), again.counts)
        assertEquals(before, dao.getAllOfBookIncludingDeleted(ExampleBook.ID))
    }
}
