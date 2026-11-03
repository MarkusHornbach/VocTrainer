package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.settings.QuizMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verb training is separate from daily goal, streak, points and figure. */
class VerbRewardsTest {
    @Test
    fun onlyVocabularyRoundsFeedTheRewards() {
        assertTrue(QuizMode.VOCABULARY.countsForRewards)
        assertFalse(QuizMode.VERBS.countsForRewards)
    }

    @Test
    fun theInfoPagesSayThatVerbsGiveNothing() {
        assertTrue(InfoLine("Verben: keine Punkte") in InfoTexts.pointsLines(restartNote = false))
        assertTrue(InfoLine("Nur Vokabelrunden zählen") in InfoTexts.streakLines())
        assertTrue(InfoLine("Nur Vokabelrunden") in InfoTexts.badgeLines())
        assertTrue(InfoLine("Keine Punkte, keine Serie, keine Abzeichen") in InfoTexts.verbIntro().lines)
    }
}
