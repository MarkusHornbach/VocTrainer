package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.settings.QuizMode
import org.junit.Assert.assertFalse
import org.junit.Test

/** The removed word type "Nur mit Vorschlägen" and switch "Redewendungen abfragen" appear in no text. */
class PhraseTextsTest {
    private val removed = listOf("Nur mit Vorschlägen", "Redewendungen abfragen")

    @Test
    fun textsNameNeitherTheOldWordTypeNorTheSwitch() {
        val levelLines = QuizMode.entries.flatMap(InfoTexts::levelBlocks).flatMap { it.lines }
        val texts = levelLines.flatMap { listOfNotNull(it.text, it.note) }
        for (text in texts) for (term in removed) assertFalse(text, text.contains(term))
    }
}
