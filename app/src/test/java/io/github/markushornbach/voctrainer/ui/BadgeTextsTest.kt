package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.BadgeKey
import io.github.markushornbach.voctrainer.learning.BadgeProgress
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeTextsTest {
    @Test
    fun rowTitleNamesTheHighestTier() {
        assertEquals("Serie · Gold", BadgeTexts.title(BadgeProgress(BadgeSeries.STREAK, 3, 31)))
        assertEquals("Gelernte Wörter · noch kein Abzeichen", BadgeTexts.title(BadgeProgress(BadgeSeries.SURE_WORDS, 0, 4)))
        assertEquals("Gelernte Wörter · Platin", BadgeTexts.title(BadgeProgress(BadgeSeries.SURE_WORDS, 4, 600)))
    }

    @Test
    fun rowValuesInSingularAndPlural() {
        assertEquals("Längste Serie: 1 Tag", BadgeTexts.value(BadgeSeries.STREAK, 1))
        assertEquals("Längste Serie: 12 Tage", BadgeTexts.value(BadgeSeries.STREAK, 12))
        assertEquals("Längste Serie: 0 Tage", BadgeTexts.value(BadgeSeries.STREAK, 0))
        assertEquals("Jetzt: 1 gelerntes Wort", BadgeTexts.value(BadgeSeries.SURE_WORDS, 1))
        assertEquals("Jetzt: 42 gelernte Wörter", BadgeTexts.value(BadgeSeries.SURE_WORDS, 42))
    }

    @Test
    fun nextLineNamesTheNextThresholdOrAllReached() {
        assertEquals("Nächstes Abzeichen bei 3", BadgeTexts.next(BadgeProgress(BadgeSeries.STREAK, 0, 1)))
        assertEquals("Nächstes Abzeichen bei 150", BadgeTexts.next(BadgeProgress(BadgeSeries.SURE_WORDS, 2, 70)))
        assertEquals("Alle Abzeichen erreicht!", BadgeTexts.next(BadgeProgress(BadgeSeries.SURE_WORDS, 4, 501)))
    }

    @Test
    fun descriptionReadsSeriesTierAndValue() {
        assertEquals(
            "Serie · Silber. Längste Serie: 8 Tage. Nächstes Abzeichen bei 30",
            BadgeTexts.description(BadgeProgress(BadgeSeries.STREAK, 2, 8))
        )
    }

    @Test
    fun resultLinesNameTierAndThreshold() {
        assertEquals("Neues Abzeichen: Silber · 7 Tage Serie", BadgeTexts.earned(BadgeKey(BadgeSeries.STREAK, 2)))
        assertEquals("Neues Abzeichen: Bronze · 10 gelernte Wörter", BadgeTexts.earned(BadgeKey(BadgeSeries.SURE_WORDS, 1)))
        assertEquals("Neues Abzeichen: Platin · 100 Tage Serie", BadgeTexts.earned(BadgeKey(BadgeSeries.STREAK, 4)))
    }
}
