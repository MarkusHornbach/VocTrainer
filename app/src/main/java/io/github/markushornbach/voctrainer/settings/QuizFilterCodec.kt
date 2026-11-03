package io.github.markushornbach.voctrainer.settings

import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class StoredQuizFilter(
    /** "all", "none" (= without unit) or the unit number. */
    val unit: String = "all",
    val parts: List<String?> = emptyList(),
    val pageFrom: Int? = null,
    val pageTo: Int? = null
    // Older selections may still contain "includePhrases" of the removed phrases switch; ignored.
)

/** Stores the last quiz selection as JSON in the preferences. */
object QuizFilterCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(filter: QuizFilter): String = json.encodeToString(
        StoredQuizFilter(
            unit = when (val unit = filter.unit) {
                UnitChoice.All -> "all"
                UnitChoice.WithoutUnit -> "none"
                is UnitChoice.Number -> unit.unit.toString()
            },
            parts = filter.parts.toList(),
            pageFrom = filter.pageFrom,
            pageTo = filter.pageTo
        )
    )

    /** Returns null for anything unreadable; the caller falls back to the default selection. */
    fun decode(text: String): QuizFilter? {
        val stored = try {
            json.decodeFromString<StoredQuizFilter>(text)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        val unit = when (stored.unit) {
            "all" -> UnitChoice.All
            "none" -> UnitChoice.WithoutUnit
            else -> stored.unit.toIntOrNull()?.let { UnitChoice.Number(it) } ?: return null
        }
        return QuizFilter(unit, stored.parts.toSet(), stored.pageFrom, stored.pageTo)
    }
}
