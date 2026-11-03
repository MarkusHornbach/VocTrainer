package io.github.markushornbach.voctrainer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Without "AllBooks" in the name, the queries see only the active book. */
@Dao
interface PracticeDayDao {
    @Query("SELECT * FROM practice_day WHERE bookId = $ACTIVE_BOOK")
    fun observeAll(): Flow<List<PracticeDay>>

    @Query("SELECT * FROM practice_day WHERE bookId = $ACTIVE_BOOK")
    suspend fun getAll(): List<PracticeDay>

    /** All books (backup, tips). */
    @Query("SELECT * FROM practice_day")
    suspend fun getAllBooks(): List<PracticeDay>

    @Query("SELECT * FROM practice_day WHERE bookId = :bookId AND date = :date AND deviceId = :deviceId")
    suspend fun get(bookId: String, date: String, deviceId: String): PracticeDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: PracticeDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<PracticeDay>)

    /** "Sicherung wiederherstellen" only. */
    @Query("DELETE FROM practice_day")
    suspend fun deleteAll()
}
