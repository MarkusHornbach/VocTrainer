package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachabilityTest {
    private fun typeable(id: String) = word(id, "dog", "Hund").copy(coreWord = "dog")
    private fun withoutCoreWord(id: String) = word(id, "dog", "Hund")
    private fun phrase(id: String) = word(id, "How are you?", "Wie geht es dir?", type = WordType.PHRASE)
    private fun verb(id: String) = word(id, "go", "gehen").copy(verbForms = VerbForms("went", "gone"))

    @Test
    fun everyWordCanReachLevelFiveSinceTypingExists() {
        assertEquals(Leitner.MAX_LEVEL, Reachability.maxLevel(typeable("a")))
        assertEquals(Leitner.MAX_LEVEL, Reachability.maxLevel(withoutCoreWord("a")))
        assertEquals(Leitner.MAX_LEVEL, Reachability.maxLevel(verb("a")))
    }

    @Test
    fun allAskableWordsCanBecomeSureIncludingTypeableOnes() {
        val words = List(5) { typeable("t$it") } + List(3) { withoutCoreWord("c$it") } + List(2) { phrase("p$it") }
        // Phrases always count.
        assertEquals(Reach(askable = 10, possibleSureWords = 10), Reachability.of(words))
        assertFalse(Reachability.of(words).hasCappedWords)
        val reach = Reachability.of(List(200) { typeable("t$it") })
        (1..FigureRules.MAX_STAGE).forEach { assertFalse(reach.stageLocked(it)) }
    }

    @Test
    fun stageIsLockedOnlyIfItNeedsMoreSureWordsThanPossible() {
        // 200 askable, 12 can become sure: stage 3 needs 10, stage 4 needs 25.
        val reach = Reach(askable = 200, possibleSureWords = 12)
        assertFalse(reach.stageLocked(1))
        assertFalse(reach.stageLocked(2))
        assertFalse(reach.stageLocked(3))
        assertTrue(reach.stageLocked(4))
        assertTrue(reach.stageLocked(FigureRules.MAX_STAGE))
    }

    @Test
    fun stagesWithoutSureWordsAreNeverLocked() {
        val nothingPossible = Reach(askable = 200, possibleSureWords = 0)
        assertFalse(nothingPossible.stageLocked(1))
        assertFalse(nothingPossible.stageLocked(2))
        assertTrue(nothingPossible.stageLocked(3))
    }

    @Test
    fun smallVocabularyUsesTheCappedRequirement() {
        // 20 askable → at most 16 sure words; 16 can become sure.
        val reach = Reach(askable = 20, possibleSureWords = 16)
        (1..FigureRules.MAX_STAGE).forEach { assertFalse(reach.stageLocked(it)) }
    }

    @Test
    fun noLevelIsLockedForTypeableWords() {
        assertTrue((0..Leitner.MAX_LEVEL).none { Reachability.levelLockedForTypeable(it) })
    }
}
