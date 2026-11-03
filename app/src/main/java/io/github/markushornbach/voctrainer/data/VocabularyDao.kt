package io.github.markushornbach.voctrainer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Without "AllBooks" or a book id in the name, the queries see only the active book. */
@Dao
interface VocabularyDao {
    @Query("SELECT COUNT(*) FROM vocabulary WHERE isDeleted = 0 AND bookId = $ACTIVE_BOOK")
    fun countActive(): Flow<Int>

    /** rowid grows with every insert, so this is the order in which words were imported/added. */
    @Query("SELECT * FROM vocabulary WHERE isDeleted = 0 AND bookId = $ACTIVE_BOOK ORDER BY rowid")
    fun getAllInInsertionOrder(): Flow<List<Vocabulary>>

    @Query("SELECT * FROM vocabulary WHERE bookId = $ACTIVE_BOOK")
    suspend fun getAllIncludingDeleted(): List<Vocabulary>

    @Query("SELECT * FROM vocabulary WHERE bookId = :bookId")
    suspend fun getAllOfBookIncludingDeleted(bookId: String): List<Vocabulary>

    /** Every book, also ignored entries (core words, the book list). */
    @Query("SELECT * FROM vocabulary")
    suspend fun getAllBooksIncludingDeleted(): List<Vocabulary>

    /** Active entries of every book, for the counts of the book list. */
    @Query("SELECT * FROM vocabulary WHERE isDeleted = 0")
    fun observeAllBooksActive(): Flow<List<Vocabulary>>

    /** Active and ignored (soft-deleted) entries in insertion order, for the vocabulary editor. */
    @Query("SELECT * FROM vocabulary WHERE bookId = $ACTIVE_BOOK ORDER BY rowid")
    fun observeAllIncludingDeletedInInsertionOrder(): Flow<List<Vocabulary>>

    /** Every book in insertion order, once; the backup keeps this order. */
    @Query("SELECT * FROM vocabulary ORDER BY rowid")
    suspend fun getAllBooksIncludingDeletedInInsertionOrder(): List<Vocabulary>

    /** "Sicherung wiederherstellen" only. */
    @Query("DELETE FROM vocabulary")
    suspend fun deleteAll()

    /** Test app only ("Alle Vokabeln entfernen"): the active book. */
    @Query("DELETE FROM vocabulary WHERE bookId = $ACTIVE_BOOK")
    suspend fun deleteAllOfActiveBook()

    /** Any book. */
    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getById(id: String): Vocabulary?

    @Insert
    suspend fun insert(vocabulary: Vocabulary)

    @Insert
    suspend fun insertAll(vocabulary: List<Vocabulary>)

    @Update
    suspend fun update(vocabulary: Vocabulary)

    @Update
    suspend fun updateAll(vocabulary: List<Vocabulary>)
}
