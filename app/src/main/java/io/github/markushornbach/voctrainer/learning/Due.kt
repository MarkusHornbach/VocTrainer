package io.github.markushornbach.voctrainer.learning

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Spaced repetition: when a word is asked again, in calendar days of the device time zone. */
object Due {
    /**
     * The day from which the word is due again, or null if it is always due (level 0, or a
     * level ≥ 1 without a review date).
     */
    fun dueDate(level: Int, lastReviewedAt: Long?, zone: ZoneId): LocalDate? {
        if (level <= 0 || lastReviewedAt == null) return null
        val reviewed = Instant.ofEpochMilli(lastReviewedAt).atZone(zone).toLocalDate()
        return reviewed.plusDays(Leitner.intervalDays(level).toLong())
    }

    fun isDue(level: Int, lastReviewedAt: Long?, today: LocalDate, zone: ZoneId): Boolean {
        val date = dueDate(level, lastReviewedAt, zone) ?: return true
        return !today.isBefore(date)
    }

    fun isDue(word: QuizWord, today: LocalDate, zone: ZoneId): Boolean =
        isDue(word.leitnerLevel, word.lastReviewedAt, today, zone)

    /** Due reviews plus level-0 words, i.e. everything a round would take first. */
    fun countDue(words: List<QuizWord>, today: LocalDate, zone: ZoneId): Int = words.count { isDue(it, today, zone) }

    /** True if the previous review was on an earlier calendar day than [today]; never reviewed counts as earlier. */
    fun reviewedEarlierDay(lastReviewedAt: Long?, today: LocalDate, zone: ZoneId): Boolean {
        if (lastReviewedAt == null) return true
        return Instant.ofEpochMilli(lastReviewedAt).atZone(zone).toLocalDate().isBefore(today)
    }
}
