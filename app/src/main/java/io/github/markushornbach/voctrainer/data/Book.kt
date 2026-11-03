package io.github.markushornbach.voctrainer.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A vocabulary book with its own learning progress. Vocabulary, irregular verbs, practice and
 * points days, figures and badges carry its [id] as `bookId`. Exactly one book is active whenever
 * at least one exists; the app shows and counts only the active book.
 */
@Entity(tableName = "book")
data class Book(
    /** Stable id from the import file, e.g. "beispielbuch". */
    @PrimaryKey
    val id: String,
    /** Display name, e.g. "Beispielbuch Englisch"; an import with another title updates it. */
    val title: String,
    /** Language of the vocabulary, e.g. "en". */
    val language: String,
    val isActive: Boolean = false,
    /** The saved vocabulary selection (see [io.github.markushornbach.voctrainer.settings.QuizFilterCodec]); null = default. */
    val quizFilter: String? = null,
    /** The saved verb selection, like [quizFilter]. */
    val verbQuizFilter: String? = null,
    /**
     * Figures with a lower slot belong to an earlier start of the book ("Buch neu beginnen"): never
     * current and not counted for the avatar number; finished ones stay in the collection.
     */
    @ColumnInfo(defaultValue = "0")
    val figureSlotStart: Int = 0
) {
    companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}

/**
 * The books of a backup or restore with exactly one active book (if any): the first active one
 * stays active, or the first book if none is active.
 */
fun List<Book>.withOneActive(): List<Book> {
    val active = firstOrNull { it.isActive } ?: firstOrNull() ?: return this
    return map { it.copy(isActive = it.id == active.id) }
}

/** SQL for "the id of the active book", for the queries that show only the active book. */
internal const val ACTIVE_BOOK = "(SELECT id FROM book WHERE isActive = 1)"

@Dao
interface BookDao {
    @Query("SELECT * FROM book ORDER BY rowid")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM book ORDER BY rowid")
    suspend fun getAll(): List<Book>

    @Query("SELECT * FROM book WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<Book?>

    @Query("SELECT * FROM book WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Book?

    @Query("SELECT * FROM book WHERE id = :id")
    suspend fun getById(id: String): Book?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: Book)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(books: List<Book>)

    @Update
    suspend fun update(book: Book)

    /** Makes [id] the only active book; call in a transaction and only for an existing id. */
    @Query("UPDATE book SET isActive = (id = :id)")
    suspend fun setActive(id: String)

    /** "Sicherung wiederherstellen" only. */
    @Query("DELETE FROM book")
    suspend fun deleteAll()
}
