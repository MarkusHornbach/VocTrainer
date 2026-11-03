package io.github.markushornbach.voctrainer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.markushornbach.voctrainer.learning.BadgeKey
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import io.github.markushornbach.voctrainer.learning.BadgeValues
import io.github.markushornbach.voctrainer.learning.FigureRules
import kotlinx.coroutines.flow.Flow

/** A badge earned in one book (see [io.github.markushornbach.voctrainer.learning.BadgeRules]); never removed by awarding. */
@Entity(tableName = "badge", primaryKeys = ["bookId", "series", "tier"])
data class Badge(
    /** [BadgeSeries.key]; a stable string, also in the backup file. */
    val series: String,
    /** 1..4 (Bronze, Silber, Gold, Platin). */
    val tier: Int,
    /** When the badge was earned (epoch millis). */
    val reachedAt: Long,
    /** [Book.id]; set by the repository on every write. */
    val bookId: String = ""
) {
    /** Null for a series this app version does not know. */
    val key: BadgeKey? get() = BadgeSeries.fromKey(series)?.let { BadgeKey(it, tier) }
}

/** The earned badges of known series; rows of an unknown series are ignored. */
fun List<Badge>.keys(): Set<BadgeKey> = mapNotNull { it.key }.toSet()

/**
 * The badge values of [vocabulary] (all rows; ignored ones do not count) and [longestStreak]:
 * sure words as the figure counts them (phrases included).
 */
fun badgeValues(vocabulary: List<Vocabulary>, longestStreak: Int): BadgeValues {
    val active = vocabulary.filterNot { it.isDeleted }
    return BadgeValues(
        longestStreak = longestStreak,
        sureWords = FigureRules.countWords(active.map { it.toQuizWord() }).sure
    )
}

/** Without "AllBooks" in the name, the queries see only the active book. */
@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge WHERE bookId = $ACTIVE_BOOK")
    fun observeAll(): Flow<List<Badge>>

    @Query("SELECT * FROM badge WHERE bookId = $ACTIVE_BOOK")
    suspend fun getAll(): List<Badge>

    /** All books (backup, tips). */
    @Query("SELECT * FROM badge")
    suspend fun getAllBooks(): List<Badge>

    /** Restore: writes the rows as they are. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(badges: List<Badge>)

    /** Awarding: an existing badge row is never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(badges: List<Badge>)

    /** "Sicherung wiederherstellen". */
    @Query("DELETE FROM badge")
    suspend fun deleteAll(): Int

    /** "Buch neu beginnen" and the test app ("Abzeichen zurücksetzen"): the badges of the active book. */
    @Query("DELETE FROM badge WHERE bookId = $ACTIVE_BOOK")
    suspend fun deleteAllOfActiveBook(): Int
}
