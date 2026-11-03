package io.github.markushornbach.voctrainer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.markushornbach.voctrainer.learning.AvatarPlan
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.WordCounts
import kotlinx.coroutines.flow.Flow

/**
 * A figure the learner grows. Slot 0 is the first figure, slot 1 the one chosen after it was
 * finished, and so on; the current figure is the highest slot if it is not finished. An unfinished
 * figure below the highest slot was given up (a backup may contain one): it is neither current
 * nor in the collection. Figures below [Book.figureSlotStart] belong to an earlier
 * start of the book ("Buch neu beginnen", see [ofStart]).
 */
@Entity(tableName = "figure", primaryKeys = ["bookId", "slot"])
data class Figure(
    /** Counted per book. */
    val slot: Int,
    /** [FigureKind.key]; a stable string, also in the backup file. */
    val kind: String,
    /** Total points when the figure was started; its stage counts the points since then. */
    val pointsAtStart: Int,
    /** Reached stage 0..[FigureRules.MAX_STAGE]; never lowered. */
    val stage: Int = 0,
    val startedAt: Long,
    /** Set when stage 10 is reached; the figure is then in the collection. */
    val finishedAt: Long? = null,
    val updatedAt: Long,
    /** [Book.id]; set by the repository on every write. */
    val bookId: String = ""
) {
    val figureKind: FigureKind? get() = FigureKind.fromKey(kind)

    val isFinished: Boolean get() = finishedAt != null
}

/**
 * The figures of the current start of the book: slot ≥ [slotStart] ([Book.figureSlotStart]). The
 * one place for this rule: figures of an earlier start are never current and do not count for the
 * avatar number; finished ones stay in the [collection].
 */
fun List<Figure>.ofStart(slotStart: Int): List<Figure> = filter { it.slot >= slotStart }

/**
 * The figure being grown: the highest slot, if it is not finished and belongs to the current start
 * ([ofStart]); null = the learner chooses one. A lower unfinished slot was given up and never comes back.
 */
fun List<Figure>.current(slotStart: Int): Figure? = ofStart(slotStart).maxByOrNull { it.slot }?.takeUnless { it.isFinished }

/** The avatar number (finished figures of the current start + 1) and its requirements in a book with [askable] entries. */
fun List<Figure>.avatarPlan(askable: Int, slotStart: Int): AvatarPlan =
    AvatarPlan(FigureRules.avatarNumber(ofStart(slotStart).count { it.isFinished }), askable)

/** Finished figures of every start of the book, oldest first. */
fun List<Figure>.collection(): List<Figure> = filter { it.isFinished }.sortedBy { it.slot }

/**
 * The figure to start when the learner chooses [kind]: the next free slot (never below [slotStart]);
 * the first figure of the current start begins with 0 points (it includes the points earned since
 * the start), every later one with [totalPoints]. Null while a figure is still growing, and while
 * a new avatar is paused (see [AvatarPlan.canStart], [askable] = askable entries of the book).
 */
fun List<Figure>.startNext(kind: FigureKind, totalPoints: Int, now: Long, askable: Int, slotStart: Int): Figure? {
    if (current(slotStart) != null || !avatarPlan(askable, slotStart).canStart) return null
    return Figure(
        slot = maxOf(nextSlot(), slotStart),
        kind = kind.key,
        pointsAtStart = if (ofStart(slotStart).isEmpty()) 0 else totalPoints,
        startedAt = now,
        updatedAt = now
    )
}

/** The slot after the highest one; 0 without figures. "Buch neu beginnen" makes it the new [Book.figureSlotStart]. */
fun List<Figure>.nextSlot(): Int = (maxOfOrNull { it.slot } ?: -1) + 1

/**
 * The figure after a stage update with [pointsSinceStart], [counts] and its [avatarNumber], or null if the stage
 * does not change. Reaching the last stage finishes it.
 */
fun Figure.grown(pointsSinceStart: Int, counts: WordCounts, avatarNumber: Int, now: Long): Figure? {
    val stage = FigureRules.newStage(this.stage, pointsSinceStart, counts, avatarNumber)
    val unfinishedLastStage = stage >= FigureRules.MAX_STAGE && finishedAt == null
    if (stage == this.stage && !unfinishedLastStage) return null
    return copy(
        stage = stage,
        finishedAt = if (stage >= FigureRules.MAX_STAGE) now else finishedAt,
        updatedAt = now
    )
}

/**
 * Test app only ("Stufe setzen"): the figure on [stage], ignoring the requirements. The last
 * stage finishes it as in [grown]; a lower stage makes a finished figure current again.
 */
fun Figure.withStage(stage: Int, now: Long): Figure {
    val value = stage.coerceIn(0, FigureRules.MAX_STAGE)
    return copy(
        stage = value,
        finishedAt = if (value >= FigureRules.MAX_STAGE) finishedAt ?: now else null,
        updatedAt = now
    )
}

/** Without "AllBooks" in the name, the queries see only the active book. */
@Dao
interface FigureDao {
    @Query("SELECT * FROM figure WHERE bookId = $ACTIVE_BOOK")
    fun observeAll(): Flow<List<Figure>>

    @Query("SELECT * FROM figure WHERE bookId = $ACTIVE_BOOK")
    suspend fun getAll(): List<Figure>

    @Query("SELECT * FROM figure WHERE bookId = :bookId")
    suspend fun getAllOfBook(bookId: String): List<Figure>

    /** All books (backup, restore, tips). */
    @Query("SELECT * FROM figure")
    suspend fun getAllBooks(): List<Figure>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(figure: Figure)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(figures: List<Figure>)

    /** "Sicherung wiederherstellen". */
    @Query("DELETE FROM figure")
    suspend fun deleteAll(): Int

    /** Test app ("Avatar zurücksetzen"): the figures of the active book. */
    @Query("DELETE FROM figure WHERE bookId = $ACTIVE_BOOK")
    suspend fun deleteAllOfActiveBook(): Int
}
