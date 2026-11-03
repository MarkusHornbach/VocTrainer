package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.QuizFilterCodec
import io.github.markushornbach.voctrainer.settings.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTest {
    /** Each book keeps its own vocabulary and verb selection. */
    @Test
    fun quizSelectionIsStoredPerBookAndMode() {
        val unit2 = QuizFilter(UnitChoice.Number(2), setOf("B"), 10, 12)
        val sixth = Book("testbuch-6", "Klasse 6", "en", quizFilter = QuizFilterCodec.encode(unit2))
        val seventh = Book("englisch-klasse-7", "Klasse 7", "en", verbQuizFilter = QuizFilterCodec.encode(QuizFilter(UnitChoice.Number(1))))

        assertEquals(unit2, sixth.quizFilter(QuizMode.VOCABULARY))
        assertNull(sixth.quizFilter(QuizMode.VERBS))
        assertNull(seventh.quizFilter(QuizMode.VOCABULARY))
        assertEquals(QuizFilter(UnitChoice.Number(1)), seventh.quizFilter(QuizMode.VERBS))
        // Something unreadable counts as no saved selection.
        assertNull(sixth.copy(quizFilter = "{").quizFilter(QuizMode.VOCABULARY))
    }

    @Test
    fun exactlyOneBookIsActive() {
        val a = Book("a", "A", "en")
        val b = Book("b", "B", "en")
        assertEquals(listOf(true, false), listOf(a, b).withOneActive().map { it.isActive })
        assertEquals(listOf(false, true), listOf(a, b.copy(isActive = true)).withOneActive().map { it.isActive })
        assertEquals(
            listOf(true, false),
            listOf(a.copy(isActive = true), b.copy(isActive = true)).withOneActive().map { it.isActive }
        )
        assertTrue(emptyList<Book>().withOneActive().isEmpty())
    }
}
