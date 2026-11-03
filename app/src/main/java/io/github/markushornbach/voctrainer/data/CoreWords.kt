package io.github.markushornbach.voctrainer.data

/**
 * The core word ("Wort zum Tippen") of a vocabulary entry: the one word of its text the learner
 * types from level 3 on. Every active entry has one; [normalize] gives it to entries that came
 * without one (e.g. from a backup) or ignores them.
 */
object CoreWords {
    private val brackets = Regex("""\([^)]*\)|\[[^\]]*\]""")
    private val separators = Regex("""[\s/]+""")

    /** Punctuation around a word; periods are handled separately so "e.g." stays whole. */
    private val trimmed = charArrayOf('(', ')', '[', ']', ',', ';', ':', '!', '?', '…', '"', '“', '”', '„')

    /** Placeholders that are never the word to type ("care for sb."). */
    private val placeholders = setOf("sb", "sth", "sb's", "sth's", "one's", "somebody's", "someone's")

    /**
     * The words of [text] that can be typed, in order and without duplicates (ignoring case):
     * without bracketed parts ("(to)", "(not)"), placeholders (sb., sth.) and "…"/"...", trimmed
     * of punctuation. A final period stays only on abbreviations with inner periods ("e.g.").
     */
    fun candidates(text: String): List<String> =
        text.replace(brackets, " ").split(separators)
            .map(::word)
            .filter { word -> word.any { it.isLetter() } && !isPlaceholder(word) }
            .distinctBy { it.lowercase() }

    /** The only candidate of [text] (as written there), or null if there is none or more than one. */
    fun derive(text: String): String? = candidates(text).singleOrNull()

    /** The candidate of [text] that equals [word] ignoring case (as written in [text]), or null. */
    fun candidateFor(word: String?, text: String): String? =
        word?.let { wanted -> candidates(text).firstOrNull { it.equals(wanted.trim(), ignoreCase = true) } }

    /**
     * The entry as every device stores it (idempotent): an active entry loses its old "multiple
     * choice only" mark and gets [derive] as core word if it has none; if that is not
     * unambiguous, the entry is ignored. Levels and updatedAt never change (this is no edit of
     * the learner); ignored entries stay as they are.
     */
    fun normalize(entry: Vocabulary): Vocabulary {
        if (entry.isDeleted) return entry
        val cleared = if (entry.choiceOnly) entry.copy(choiceOnly = false) else entry
        if (!cleared.coreWord.isNullOrBlank()) return cleared
        val derived = derive(entry.english)
        return if (derived != null) cleared.copy(coreWord = derived) else cleared.copy(coreWord = null, isDeleted = true)
    }

    /** The entries of [entries] that [normalize] changes, already changed. */
    fun normalizeAll(entries: List<Vocabulary>): List<Vocabulary> =
        entries.mapNotNull { entry -> normalize(entry).takeIf { it != entry } }

    private fun word(token: String): String {
        val word = token.trim(*trimmed).trimStart('.')
        val withoutPeriods = word.trimEnd('.')
        return if ('.' in withoutPeriods) word else withoutPeriods
    }

    private fun isPlaceholder(word: String): Boolean = word.lowercase().replace('’', '\'') in placeholders
}
