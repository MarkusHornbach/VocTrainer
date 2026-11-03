package io.github.markushornbach.voctrainer.settings

import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class QuizFilterCodecTest {
    @Test
    fun roundTripKeepsEverySelection() {
        listOf(
            QuizFilter(),
            QuizFilter(UnitChoice.Number(1), setOf(null, "A"), 150, 152),
            QuizFilter(UnitChoice.WithoutUnit, setOf("Pet diary"), 153, null),
            QuizFilter(UnitChoice.Number(12))
        ).forEach { filter ->
            assertEquals(filter, QuizFilterCodec.decode(QuizFilterCodec.encode(filter)))
        }
    }

    @Test
    fun oldSelectionsWithThePhrasesSwitchStillDecode() {
        val old = """{"unit": "1", "parts": [null, "A"], "pageFrom": 150, "pageTo": 152, "includePhrases": true}"""
        assertEquals(QuizFilter(UnitChoice.Number(1), setOf(null, "A"), 150, 152), QuizFilterCodec.decode(old))
        // The removed switch is never written with the selection.
        val encoded = QuizFilterCodec.encode(QuizFilter(UnitChoice.Number(1)))
        assertFalse(encoded.contains("includePhrases"))
    }

    @Test
    fun unreadableValuesAreIgnored() {
        assertNull(QuizFilterCodec.decode("not json"))
        assertNull(QuizFilterCodec.decode("""{"unit": "seven"}"""))
        assertEquals(QuizFilter(), QuizFilterCodec.decode("""{"future": true}"""))
    }

    @Test
    fun quizModeKeysFallBackToVocabulary() {
        assertEquals(QuizMode.VERBS, QuizMode.fromKey("verbs"))
        assertEquals(QuizMode.VOCABULARY, QuizMode.fromKey(null))
        assertEquals(QuizMode.VOCABULARY, QuizMode.fromKey("unknown"))
    }

    @Test
    fun themeKeysFallBackToDefault() {
        assertEquals(AppTheme.OCEAN, AppTheme.fromKey("ocean"))
        assertEquals(AppTheme.GAMING_DARK, AppTheme.fromKey("gaming_dark"))
        // Nothing stored (never changed) or unknown: the default "Ozean".
        assertEquals(AppTheme.OCEAN, AppTheme.fromKey(null))
        assertEquals(AppTheme.OCEAN, AppTheme.fromKey("purple"))
    }
}
