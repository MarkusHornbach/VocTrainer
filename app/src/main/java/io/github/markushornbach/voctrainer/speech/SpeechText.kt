package io.github.markushornbach.voctrainer.speech

/** Turns stored entries into text the speech engine reads well. */
object SpeechText {
    private val ellipsis = Regex("…|\\.\\.\\.")
    private val somebody = Regex("\\b([Ss])b\\.")
    private val something = Regex("\\b([Ss])th\\.")
    private val slash = Regex("\\s*/\\s*")
    private val whitespace = Regex("\\s+")

    /**
     * "(to) be allowed to" → "to be allowed to", "(to) take sb. for a walk" → "to take somebody for a walk",
     * "every Monday/Tuesday/…" → "every Monday or Tuesday", "was / were" → "was or were".
     */
    fun english(text: String): String {
        val plain = text
            .replace(ellipsis, " ")
            .replace("(", "")
            .replace(")", "")
            .replace(somebody) { if (it.groupValues[1] == "S") "Somebody" else "somebody" }
            .replace(something) { if (it.groupValues[1] == "S") "Something" else "something" }
        return plain.split(slash)
            .map { it.replace(whitespace, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" or ")
    }
}
