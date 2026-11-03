package io.github.markushornbach.voctrainer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "irregular_verb")
data class IrregularVerb(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val infinitive: String,
    val simplePast: String,
    val pastParticiple: String? = null,
    /** Ordered; the first element is the main meaning. */
    val germanMeanings: List<String>,
    val unit: Int,
    val part: String? = null,
    val page: Int? = null,
    /** True if the German meaning was not printed in the textbook. */
    val germanAdded: Boolean = false,
    val leitnerLevel: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    /** Last first-attempt answer in a round (epoch millis); drives when the verb is due again. */
    val lastReviewedAt: Long? = null,
    /** [Book.id] of the book the verb belongs to; set by the repository on every insert. */
    val bookId: String = ""
)
