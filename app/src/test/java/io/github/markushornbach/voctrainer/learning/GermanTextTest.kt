package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanTextTest {
    private fun strip(text: String) = GermanText.withoutGenderSuffix(text)

    @Test
    fun removesGenderSuffixes() {
        assertEquals("Tänzer", strip("Tänzer/in"))
        assertEquals("(Haus-)Besitzer", strip("(Haus-)Besitzer/in"))
        assertEquals("Lehrer", strip("Lehrer/-in"))
        assertEquals("Lehrer", strip("Lehrer/innen"))
        assertEquals("Schüler", strip("Schüler*innen"))
        assertEquals("Lehrer", strip("Lehrer:in"))
        assertEquals("Lehrer", strip("Lehrer_in"))
        assertEquals("Lehrer", strip("Lehrer(in)"))
        assertEquals("Lehrer", strip("LehrerIn"))
        assertEquals("Nachbar, Freund", strip("Nachbar/in, Freund/in"))
    }

    @Test
    fun keepsTextThatIsNotAGenderSuffix() {
        listOf(
            "Uhr (bei Uhrzeiten)",
            "ein(e) kleine(r, s)",
            "noch ein(e)",
            "drinnen",
            "Ich bin in Berlin.",
            "Mann/Frau",
            "Fahrten/Karussells",
            "jeden Montag/Dienstag/…",
            "Insel"
        ).forEach { assertEquals(it, strip(it)) }
    }

    @Test
    fun exampleFileHasNoGenderSuffixLeftAfterStripping() {
        val genderSuffix = Regex("/-?in\\b")
        val raw = exampleWords().flatMap { it.germanMeanings }
        assertEquals(8, raw.count { genderSuffix.containsMatchIn(it) })
        assertEquals(emptyList<String>(), raw.map(::strip).filter { genderSuffix.containsMatchIn(it) })
    }
}
