package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.room.Room
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/** The active book of the UI tests' databases. */
const val TEST_BOOK_ID = "test-book"

/**
 * An empty in-memory database whose queries run on its own threads, so [closeAfterRunningQueries]
 * can wait for them; the app's shared default threads cannot be waited for.
 */
fun inMemoryDatabase(context: Context): VocTrainerDatabase =
    Room.inMemoryDatabaseBuilder(context, VocTrainerDatabase::class.java)
        .setQueryExecutor(Executors.newFixedThreadPool(4))
        .build()

/** An in-memory database with the active book [TEST_BOOK_ID]; rows inserted directly need its id (see [inTestBook]). */
fun testDatabase(context: Context): VocTrainerDatabase =
    inMemoryDatabase(context).also { db ->
        runBlocking { db.bookDao().insert(Book(TEST_BOOK_ID, "Testbuch", Book.DEFAULT_LANGUAGE, isActive = true)) }
    }

/**
 * Closes a database from [inMemoryDatabase] once the queries already running on it have finished.
 * Clearing the ViewModels cancels their flows, but a query that has started keeps running; closing
 * the database under it fails the test with "the connection pool has been closed". Queries that
 * would start after this are rejected and their coroutines cancelled.
 */
fun VocTrainerDatabase.closeAfterRunningQueries() {
    (queryExecutor as? ExecutorService)?.run {
        shutdown()
        awaitTermination(5, TimeUnit.SECONDS)
    }
    close()
}

@JvmName("vocabularyInTestBook")
fun List<Vocabulary>.inTestBook(): List<Vocabulary> = map { it.copy(bookId = TEST_BOOK_ID) }

@JvmName("verbsInTestBook")
fun List<IrregularVerb>.inTestBook(): List<IrregularVerb> = map { it.copy(bookId = TEST_BOOK_ID) }
