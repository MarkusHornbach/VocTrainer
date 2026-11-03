package io.github.markushornbach.voctrainer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.WordType
import java.util.UUID

@Entity(tableName = "vocabulary")
data class Vocabulary(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val english: String,
    /** Ordered; the first element is the main meaning. */
    val germanMeanings: List<String>,
    /** Null only for entries created before units existed. */
    val unit: Int? = null,
    val unitTitle: String? = null,
    val part: String? = null,
    val extra: String? = null,
    /** Printed textbook page of the vocabulary section. */
    val page: Int? = null,
    /** Type decided by the import; null means the runtime heuristic is used. */
    val wordType: WordType? = null,
    val leitnerLevel: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    /** Last first-attempt answer in a round (epoch millis); drives when the word is due again. */
    val lastReviewedAt: Long? = null,
    /**
     * The one word a learner types ("Wort zum Tippen"); the rest of [english] is shown as a frame,
     * e.g. "(to) be ____ to" → "allowed". Given by the import file or tapped by the learner when
     * entering a word by hand. Every active entry has one (see [CoreWords.normalize]).
     */
    val coreWord: String? = null,
    /**
     * Unused: always false ([CoreWords.normalize] clears it). The column is part of the database
     * schema.
     */
    @ColumnInfo(defaultValue = "0")
    val choiceOnly: Boolean = false,
    /**
     * Highest level the word ever reached; points are only given for levels above it (see
     * [io.github.markushornbach.voctrainer.learning.Points]). Always at least [leitnerLevel]; a drop while
     * learning keeps it. Only "Buch neu beginnen" ([BookRestart]) and the test tools set it back.
     */
    @ColumnInfo(defaultValue = "0")
    val highestLevel: Int = 0,
    /**
     * When the learner first typed the target word correctly on the first attempt of a round
     * (epoch millis); kept as data for a possible later badge idea (no badge uses it now). Set once; cleared only by
     * "Buch neu beginnen" ([BookRestart]); null = never (or before this was recorded).
     */
    val firstTypedCorrectAt: Long? = null,
    /** [Book.id] of the book the entry belongs to; set by the repository on every insert. */
    val bookId: String = ""
) {
    /** This entry on [level]; raises [highestLevel] if [level] is above it, never lowers it. */
    fun withLevel(level: Int): Vocabulary = copy(leitnerLevel = level, highestLevel = maxOf(highestLevel, level))

    /** Records the first correctly typed answer at [at]; keeps an earlier date. */
    fun withFirstTypedCorrect(at: Long): Vocabulary =
        if (firstTypedCorrectAt == null) copy(firstTypedCorrectAt = at) else this

    companion object {
        const val MAX_LEITNER_LEVEL = Leitner.MAX_LEVEL
    }
}

fun Vocabulary.toQuizWord() =
    QuizWord(
        id, english, germanMeanings, unit, part, leitnerLevel, page, wordType,
        lastReviewedAt = lastReviewedAt, coreWord = coreWord
    )
