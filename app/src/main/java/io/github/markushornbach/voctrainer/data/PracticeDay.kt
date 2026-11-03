package io.github.markushornbach.voctrainer.data

import androidx.room.Entity
import io.github.markushornbach.voctrainer.learning.PracticeRecord
import java.time.LocalDate

/** Rounds finished in one book on one device on one day; summed over all devices for the daily goal. */
@Entity(tableName = "practice_day", primaryKeys = ["bookId", "date", "deviceId"])
data class PracticeDay(
    /** ISO yyyy-MM-dd in the device time zone. */
    val date: String,
    /** Random id of the installation (see `AppSettings.deviceId`). */
    val deviceId: String,
    val roundsCompleted: Int,
    /** The daily goal set on this device that day; overwritten on each update. */
    val dailyGoal: Int,
    val updatedAt: Long,
    /** [Book.id]; set by the repository on every write. */
    val bookId: String = ""
)

fun PracticeDay.toRecord() = PracticeRecord(LocalDate.parse(date), deviceId, roundsCompleted, dailyGoal)
