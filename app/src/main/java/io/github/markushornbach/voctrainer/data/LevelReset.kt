package io.github.markushornbach.voctrainer.data

/**
 * Test app tools that set the learning level of a selection. Only active items of [ids] change;
 * they get a fresh updatedAt like any other edit.
 */
object LevelReset {
    /**
     * Admin mode (only on in the test app): sets active items of [ids] to [level] (up or down)
     * and clears their review date, so they are due at once – for trying out features that
     * depend on the level. The highest level is set to [level] too, so the points for new levels
     * can be tried again. Items already on [level] without a review date stay as they are.
     */
    fun vocabularyTo(entries: List<Vocabulary>, ids: Set<String>, level: Int, now: Long): List<Vocabulary> {
        require(level in 0..Vocabulary.MAX_LEITNER_LEVEL) { "level $level" }
        return entries.filter {
            it.id in ids && !it.isDeleted && (it.leitnerLevel != level || it.lastReviewedAt != null || it.highestLevel != level)
        }.map { it.copy(leitnerLevel = level, highestLevel = level, lastReviewedAt = null, updatedAt = now) }
    }

    /** Test app: like [vocabularyTo] for irregular verbs. */
    fun verbsTo(verbs: List<IrregularVerb>, ids: Set<String>, level: Int, now: Long): List<IrregularVerb> {
        require(level in 0..Vocabulary.MAX_LEITNER_LEVEL) { "level $level" }
        return verbs.filter { it.id in ids && !it.isDeleted && (it.leitnerLevel != level || it.lastReviewedAt != null) }
            .map { it.copy(leitnerLevel = level, lastReviewedAt = null, updatedAt = now) }
    }

    /**
     * Test app ("Auswahl jetzt fällig machen"): clears the review date of active items of [ids],
     * so they are due at once; the level stays. Items without a review date are already due.
     */
    fun vocabularyDueNow(entries: List<Vocabulary>, ids: Set<String>, now: Long): List<Vocabulary> =
        entries.filter { it.id in ids && !it.isDeleted && it.lastReviewedAt != null }
            .map { it.copy(lastReviewedAt = null, updatedAt = now) }

    fun verbsDueNow(verbs: List<IrregularVerb>, ids: Set<String>, now: Long): List<IrregularVerb> =
        verbs.filter { it.id in ids && !it.isDeleted && it.lastReviewedAt != null }
            .map { it.copy(lastReviewedAt = null, updatedAt = now) }
}
