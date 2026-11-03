package io.github.markushornbach.voctrainer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.PointsRecord
import kotlinx.coroutines.flow.Flow

/** Points earned in one book on one device on one day; see [io.github.markushornbach.voctrainer.learning.Points.total]. */
@Entity(tableName = "points_day", primaryKeys = ["bookId", "date", "deviceId"])
data class PointsDay(
    /** ISO yyyy-MM-dd in the device time zone. */
    val date: String,
    /** Random id of the installation (see `AppSettings.deviceId`). */
    val deviceId: String,
    /** Points for words that were due and answered correctly at the first attempt. */
    val risePoints: Int = 0,
    /** 0, or the bonus for reaching the daily goal. */
    val dailyBonus: Int = 0,
    /** Only non-zero on the row written by the one-time calculation for existing progress. */
    val startPoints: Int = 0,
    val updatedAt: Long,
    /** [Book.id]; set by the repository on every write. */
    val bookId: String = ""
)

fun PointsDay.toRecord() = PointsRecord(date, deviceId, risePoints, dailyBonus, startPoints)

/**
 * "Buch neu beginnen" ([BookRestart]) resets the points of the book: every row with points is set
 * to 0 and gets a new updatedAt.
 */
object PointsDayReset {
    /** The rows that have to be written so that [Points.total] becomes 0; rows without points stay. */
    fun cleared(rows: List<PointsDay>, now: Long): List<PointsDay> =
        rows.filter { it.risePoints != 0 || it.dailyBonus != 0 || it.startPoints != 0 }
            .map { it.copy(risePoints = 0, dailyBonus = 0, startPoints = 0, updatedAt = now) }
}

/** Without "AllBooks" in the name, the queries see only the active book. */
@Dao
interface PointsDayDao {
    @Query("SELECT * FROM points_day WHERE bookId = $ACTIVE_BOOK")
    fun observeAll(): Flow<List<PointsDay>>

    @Query("SELECT * FROM points_day WHERE bookId = $ACTIVE_BOOK")
    suspend fun getAll(): List<PointsDay>

    @Query("SELECT * FROM points_day WHERE bookId = :bookId")
    suspend fun getAllOfBook(bookId: String): List<PointsDay>

    /** All books (backup, tips). */
    @Query("SELECT * FROM points_day")
    suspend fun getAllBooks(): List<PointsDay>

    @Query("SELECT * FROM points_day WHERE bookId = :bookId AND date = :date AND deviceId = :deviceId")
    suspend fun get(bookId: String, date: String, deviceId: String): PointsDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: PointsDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<PointsDay>)

    /** "Sicherung wiederherstellen" only. */
    @Query("DELETE FROM points_day")
    suspend fun deleteAll()
}
