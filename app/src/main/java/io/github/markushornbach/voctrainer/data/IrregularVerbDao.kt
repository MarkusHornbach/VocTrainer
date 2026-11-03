package io.github.markushornbach.voctrainer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Without "AllBooks" or a book id in the name, the queries see only the active book. */
@Dao
interface IrregularVerbDao {
    /** rowid grows with every insert, so this is the order in which verbs were imported. */
    @Query("SELECT * FROM irregular_verb WHERE isDeleted = 0 AND bookId = $ACTIVE_BOOK ORDER BY rowid")
    fun getAllInInsertionOrder(): Flow<List<IrregularVerb>>

    @Query("SELECT * FROM irregular_verb WHERE bookId = $ACTIVE_BOOK")
    suspend fun getAllIncludingDeleted(): List<IrregularVerb>

    @Query("SELECT * FROM irregular_verb WHERE bookId = :bookId")
    suspend fun getAllOfBookIncludingDeleted(bookId: String): List<IrregularVerb>

    /** Every book including deleted verbs in insertion order; the backup keeps this order. */
    @Query("SELECT * FROM irregular_verb ORDER BY rowid")
    suspend fun getAllBooksIncludingDeletedInInsertionOrder(): List<IrregularVerb>

    /** "Sicherung wiederherstellen" only. */
    @Query("DELETE FROM irregular_verb")
    suspend fun deleteAll()

    /** Test app only ("Alle Vokabeln entfernen"): the active book. */
    @Query("DELETE FROM irregular_verb WHERE bookId = $ACTIVE_BOOK")
    suspend fun deleteAllOfActiveBook()

    /** Any book. */
    @Query("SELECT * FROM irregular_verb WHERE id = :id")
    suspend fun getById(id: String): IrregularVerb?

    @Insert
    suspend fun insert(verb: IrregularVerb)

    @Insert
    suspend fun insertAll(verbs: List<IrregularVerb>)

    @Update
    suspend fun update(verb: IrregularVerb)

    @Update
    suspend fun updateAll(verbs: List<IrregularVerb>)
}
