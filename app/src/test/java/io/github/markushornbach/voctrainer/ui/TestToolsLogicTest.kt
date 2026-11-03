package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.settings.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestToolsLogicTest {
    @Test
    fun allGroupsAreClosedAtFirstAndInTheAskedOrder() {
        assertTrue(TestToolGroups.none.isEmpty())
        assertEquals(
            listOf("Lernlevel", "Avatar, Punkte und Abzeichen", "Hilfesystem", "Bücher und Vokabeln", "Prüfung testen", "Zurücksetzen"),
            TestToolGroup.entries.map { it.title }
        )
    }

    @Test
    fun toggleOpensAndClosesOneGroup() {
        val open = TestToolGroups.toggle(TestToolGroups.none, TestToolGroup.HELP)
        assertEquals(setOf(TestToolGroup.HELP), open)
        assertTrue(TestToolGroups.toggle(open, TestToolGroup.HELP).isEmpty())
    }

    @Test
    fun openAllAndCloseAll() {
        assertEquals(TestToolGroup.entries.size, TestToolGroups.all.size)
        assertTrue(TestToolGroups.none.isEmpty())
    }

    @Test
    fun savedStateRoundTripsAndIgnoresUnknownNames() {
        val open = setOf(TestToolGroup.LEVEL, TestToolGroup.RESET)
        assertEquals(open, TestToolGroups.decode(TestToolGroups.encode(open)))
        assertEquals(setOf(TestToolGroup.LEVEL), TestToolGroups.decode(listOf("LEVEL", "GONE")))
    }

    @Test
    fun groupTagsAreUnique() {
        assertEquals(TestToolGroup.entries.size, TestToolGroup.entries.map { it.tag }.toSet().size)
    }

    @Test
    fun levelToggleDefaultsToTheCurrentQuizMode() {
        val pick = LevelPick()
        assertEquals(QuizMode.VERBS, pick.mode(QuizMode.VERBS))
        assertEquals(QuizMode.VOCABULARY, pick.mode(QuizMode.VOCABULARY))
        assertEquals(QuizMode.VOCABULARY, pick.withMode(QuizMode.VOCABULARY).mode(QuizMode.VERBS))
    }

    @Test
    fun theLevelSetLastIsHighlightedAndStaysWhenPressedAgain() {
        val pick = LevelPick().afterSet(QuizMode.VOCABULARY, 3)
        assertTrue(pick.isSelected(QuizMode.VOCABULARY, 3))
        assertFalse(pick.isSelected(QuizMode.VERBS, 3))
        assertFalse(pick.isSelected(QuizMode.VOCABULARY, 2))
        assertEquals(pick, pick.afterSet(QuizMode.VOCABULARY, 3))
        assertTrue(pick.afterSet(QuizMode.VOCABULARY, 4).isSelected(QuizMode.VOCABULARY, 4))
    }

    @Test
    fun highlightIsClearedWhenTheModeToggleChanges() {
        val pick = LevelPick(QuizMode.VERBS).afterSet(QuizMode.VERBS, 2)
        assertNull(pick.withMode(QuizMode.VOCABULARY).highlight)
        // Choosing the mode that is already chosen changes nothing.
        assertEquals(pick, pick.withMode(QuizMode.VERBS))
    }

    @Test
    fun highlightIsClearedWhenTheSelectionChanges() {
        val pick = LevelPick(QuizMode.VERBS).afterSet(QuizMode.VERBS, 2).afterSelectionChange()
        assertNull(pick.highlight)
        assertEquals(QuizMode.VERBS, pick.chosenMode)
    }
}
