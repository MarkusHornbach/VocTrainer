package io.github.markushornbach.voctrainer.learning

/** Display helpers for German meanings; the stored text is never changed. */
object GermanText {
    // "Tänzer/in", "Lehrer/-innen", "Schüler*in", "Lehrer:innen", "Lehrer_in"
    private val separatorSuffix = Regex("(?<=\\p{L})[/*:_]-?in(?:nen)?(?!\\p{L})")

    // "Lehrer(in)" – but not "Uhr (bei Uhrzeiten)"
    private val parenthesisedSuffix = Regex("(?<=\\p{L})\\((?:in|innen)\\)")

    // "LehrerIn", "LehrerInnen"
    private val capitalISuffix = Regex("(?<=\\p{Ll})In(?:nen)?(?!\\p{L})")

    /** Removes gender-inclusive suffixes such as "/in": "Tänzer/in" → "Tänzer". */
    fun withoutGenderSuffix(text: String): String = text
        .replace(separatorSuffix, "")
        .replace(parenthesisedSuffix, "")
        .replace(capitalISuffix, "")
}
