package io.github.markushornbach.voctrainer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirstStartTextsTest {
    private val forbidden = listOf("fällig", "Multiple Choice", "Figur", "sicher", "zurücksetzen", "Intervall", "Eltern", "Lehrkr")

    @Test
    fun learnerTextsUseTheAgreedTerms() {
        val texts = listOf(
            FirstStartTexts.welcomeTitle(false), FirstStartTexts.WELCOME_LEARN, FirstStartTexts.WELCOME_AVATAR,
            FirstStartTexts.START, FirstStartTexts.WITHOUT_TIPS, FirstStartTexts.BOOK_TITLE, FirstStartTexts.EXAMPLE_BOOK,
            FirstStartTexts.EXAMPLE_BOOK_LINK, FirstStartTexts.OWN_BOOK, FirstStartTexts.FILE_INVALID,
            FirstStartTexts.EXAMPLE_HINT, FirstStartTexts.TIPS_OFF_HINT
        ) + FigureTexts.CHOICE_LINES
        for (text in texts) {
            for (word in forbidden) assertFalse("$text: $word", text.contains(word, ignoreCase = true))
        }
    }

    @Test
    fun welcomeBookChoiceAndHintTextsAreExact() {
        assertEquals("Willkommen bei VocTrainer!", FirstStartTexts.welcomeTitle(debug = false))
        assertEquals("Willkommen bei VocTrainer Debug!", FirstStartTexts.welcomeTitle(debug = true))
        assertEquals("Mein Buch laden", FirstStartTexts.OWN_BOOK)
        assertEquals("Keine Vokabeldatei? Beispielbuch ausprobieren", FirstStartTexts.EXAMPLE_BOOK_LINK)
        assertEquals("Dein echtes Buch kannst du später laden: ⚙️ → Vokabeln verwalten", FirstStartTexts.EXAMPLE_HINT)
        assertEquals("Tipps kannst du jederzeit einschalten: ? → Tipps wieder zeigen", FirstStartTexts.TIPS_OFF_HINT)
        assertEquals(
            listOf("Wächst mit Punkten und gelernten Wörtern", "Bleibt, bis er ausgewachsen ist – dann wählst du den nächsten"),
            FigureTexts.CHOICE_LINES
        )
    }
}
