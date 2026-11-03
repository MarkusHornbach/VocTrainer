package io.github.markushornbach.voctrainer.learning

/** Rough word class, guessed from the text at runtime (not stored). */
enum class WordType {
    VERB, PHRASE, NOUN, OTHER;

    val jsonValue: String get() = name.lowercase()

    companion object {
        private val whitespace = Regex("\\s+")
        private val leadingParenthesis = Regex("^\\([^)]*\\)\\s*")
        private val leadingArticle = Regex("^(der|die|das)\\s+")

        /** Checked in this order: VERB, PHRASE, NOUN, OTHER. */
        fun of(english: String, mainMeaning: String): WordType {
            val en = english.trim()
            val main = mainMeaning.trim()
            if (en.lowercase().startsWith("(to) ")) return VERB
            if (en.endsWith(".") || en.endsWith("?") || en.endsWith("!") || en.contains("…") ||
                en.split(whitespace).size >= 3 ||
                main.endsWith(".") || main.endsWith("?") || main.endsWith("!")
            ) {
                return PHRASE
            }
            val core = main.replace(leadingParenthesis, "").replace(leadingArticle, "")
            return if (core.firstOrNull()?.isUpperCase() == true) NOUN else OTHER
        }

        /** The single place that decides a word's type: stored type first, heuristic as fallback. */
        fun effective(storedType: WordType?, english: String, mainMeaning: String): WordType =
            storedType ?: of(english, mainMeaning)

        /** Parses the import/backup value ("verb", "noun", "phrase", "other"); anything else is null. */
        fun fromJsonValue(value: String?): WordType? = entries.firstOrNull { it.jsonValue == value?.lowercase() }
    }
}
