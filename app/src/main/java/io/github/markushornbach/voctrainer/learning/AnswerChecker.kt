package io.github.markushornbach.voctrainer.learning

/** Rules for checking typed answers; case and surrounding whitespace never matter. */
object AnswerChecker {
    private const val OPTIONAL_TO = "(to) "

    /**
     * If [expected] starts with "(to) ", the answer is correct with or without "to"/"(to)".
     * Entries without that prefix are compared as they are (e.g. "to" = "um zu").
     */
    fun isCorrectEnglish(answer: String, expected: String): Boolean {
        val given = normalizeText(answer)
        val target = normalizeText(expected)
        if (given == target) return true
        if (!target.startsWith(OPTIONAL_TO)) return false
        val base = target.removePrefix(OPTIONAL_TO)
        val givenBase = when {
            given.startsWith(OPTIONAL_TO) -> given.removePrefix(OPTIONAL_TO)
            given.startsWith("to ") -> given.removePrefix("to ")
            else -> given
        }
        return givenBase == base
    }

    /** Every German meaning counts as correct. */
    fun isCorrectGerman(answer: String, meanings: List<String>): Boolean {
        val given = normalizeText(answer)
        return given.isNotEmpty() && meanings.any { normalizeText(it) == given }
    }

    /**
     * German → English: all meanings are shown together because they disambiguate each other,
     * gender endings hidden as everywhere else ("Tänzer/in" → "Tänzer").
     */
    fun germanPrompt(meanings: List<String>): String =
        meanings.map { GermanText.withoutGenderSuffix(it) }.distinct().joinToString("; ")

    /**
     * Typing question: the core word, or the whole English entry (with or without "to" as in
     * [isCorrectEnglish]). Case, surrounding whitespace and apostrophe variants never matter. A
     * core word ending in "." also counts without that final period ("e.g." = "e.g").
     */
    fun isCorrectTyped(answer: String, word: QuizWord): Boolean {
        val given = typedKey(answer)
        if (given.isEmpty()) return false
        val core = word.coreWord?.let(::typedKey)
        if (core != null && (given == core || (core.endsWith(".") && given == core.removeSuffix(".")))) return true
        return isCorrectEnglish(given, typedKey(word.english))
    }

    /** Result of a typed answer; see [checkTyped]. */
    sealed interface TypedResult {
        data object Correct : TypedResult

        /** Another entry with a shared German meaning ("Auch richtig! Gesucht war: …"). */
        data class AlsoCorrect(val other: QuizWord) : TypedResult

        /**
         * "Fast richtig": [given] (the input as compared) is a small typo or the American spelling of
         * [expected] (the typed target of the asked word, or of [other] for [AlmostKind.TYPO_OF_OTHER]).
         * [diff] marks the differing letters; [rules] are the American rules applied.
         */
        data class AlmostCorrect(
            val kind: AlmostKind,
            val expected: String,
            val given: String,
            val diff: SpellingDiff,
            val other: QuizWord? = null,
            val rules: List<String> = emptyList()
        ) : TypedResult

        data object Wrong : TypedResult
    }

    /** Why a typed answer is wrong (test tool "Prüfung testen"); see [whyWrong]. */
    sealed interface WrongReason {
        data object NotTypeable : WrongReason
        data object TooManyErrors : WrongReason
        data object TooShort : WrongReason

        /** The typo or American spelling is itself a word of the learning material. */
        data class MaterialWord(val word: String) : WrongReason
    }

    /**
     * In this order: correct, "auch richtig" via [index] (built from all vocabulary), the American
     * spelling or a typo of the asked word, a typo of an "auch richtig" entry ("fast richtig"), wrong.
     * A typo or American spelling that is itself a word of [material] is wrong.
     */
    fun checkTyped(
        answer: String,
        word: QuizWord,
        index: AlsoCorrectIndex,
        material: LearningMaterial = LearningMaterial.EMPTY
    ): TypedResult {
        if (isCorrectTyped(answer, word)) return TypedResult.Correct
        index.find(answer, word)?.let { return TypedResult.AlsoCorrect(it) }
        return almostCorrect(answer, word, index, material) ?: TypedResult.Wrong
    }

    private fun almostCorrect(
        answer: String, word: QuizWord, index: AlsoCorrectIndex, material: LearningMaterial
    ): TypedResult.AlmostCorrect? {
        val given = comparedInput(answer).takeIf(String::isNotEmpty) ?: return null
        if (given in material) return null
        AlsoCorrectIndex.typedTarget(word)?.let { target ->
            AmericanSpelling.variants(target)[given]?.let { rules ->
                return TypedResult.AlmostCorrect(
                    AlmostKind.AMERICAN, target, given, SpellingDiff.around(given, target), rules = rules
                )
            }
            typo(given, target)?.let { return TypedResult.AlmostCorrect(AlmostKind.TYPO, target, given, it) }
        }
        for (other in index.others(word)) {
            typo(given, other.key)?.let {
                return TypedResult.AlmostCorrect(AlmostKind.TYPO_OF_OTHER, other.key, given, it, other.word)
            }
        }
        return null
    }

    private fun typo(given: String, target: String): SpellingDiff? =
        if (Typo.longEnough(target)) Typo.diff(given, target) else null

    /**
     * For an answer that [checkTyped] calls wrong: why it is no "fast richtig". Not typeable (verbs only), the
     * near miss is a word of [material], the target is too short for a typo, or more than one mistake.
     */
    fun whyWrong(answer: String, word: QuizWord, index: AlsoCorrectIndex, material: LearningMaterial): WrongReason {
        val target = AlsoCorrectIndex.typedTarget(word)
        if (!word.isTypeable || target == null) return WrongReason.NotTypeable
        val given = comparedInput(answer)
        val targets = listOf(target) + index.others(word).map { it.key }
        val near = targets.filter { Typo.diff(given, it) != null }
        val american = AmericanSpelling.variants(target).containsKey(given)
        return when {
            (american || near.isNotEmpty()) && given in material -> WrongReason.MaterialWord(given)
            near.isNotEmpty() && near.none(Typo::longEnough) -> WrongReason.TooShort
            else -> WrongReason.TooManyErrors
        }
    }

    /** The input as compared for "fast richtig": [typedKey] without a leading "to " / "(to) ". */
    private fun comparedInput(answer: String): String {
        val key = typedKey(answer)
        return when {
            key.startsWith(OPTIONAL_TO) -> key.removePrefix(OPTIONAL_TO).trim()
            key.startsWith("to ") -> key.removePrefix("to ").trim()
            else -> key
        }
    }

    /** A typed text compared: normalized like everywhere, with every apostrophe as `'`. */
    internal fun typedKey(text: String): String = normalizeText(text.replace(apostrophes, "'"))

    private val apostrophes = Regex("[’‘ʼ`´]")
}

/** What kind of "fast richtig" a typed answer is ([AnswerChecker.TypedResult.AlmostCorrect]). */
enum class AlmostKind {
    /** One small typo of the asked word. */
    TYPO,

    /** The American spelling of the asked (British) word. */
    AMERICAN,

    /** One small typo of another entry with a shared German meaning ("Auch richtig" with a typo). */
    TYPO_OF_OTHER
}

/**
 * Lookup for "Auch richtig": other entries sharing at least one German meaning with the asked
 * one, and what typing them means. Built once per round from all vocabulary, so a lookup is a
 * few map reads and never a scan of the vocabulary.
 */
class AlsoCorrectIndex(words: List<QuizWord>) {
    /** An entry and what typing it means ([typedTarget]). */
    class Target(val word: QuizWord, val key: String)

    private val byMeaning: Map<String, List<Target>> = buildMap<String, MutableList<Target>> {
        for (word in words) {
            if (word.verbForms != null) continue
            val key = typedTarget(word) ?: continue
            val target = Target(word, key)
            for (meaning in word.germanMeanings.mapTo(HashSet(), ::comparableMeaning)) {
                if (meaning.isNotEmpty()) getOrPut(meaning) { mutableListOf() } += target
            }
        }
    }

    /** Another entry than [asked] that [answer] matches and that shares a German meaning with it. */
    fun find(answer: String, asked: QuizWord): QuizWord? {
        val given = AnswerChecker.typedKey(answer)
        if (given.isEmpty()) return null
        return others(asked).firstOrNull { it.key == given }?.word
    }

    /** All other entries than [asked] sharing a German meaning with it, in the order of its meanings. */
    fun others(asked: QuizWord): List<Target> =
        asked.germanMeanings.flatMap { byMeaning[comparableMeaning(it)].orEmpty() }
            .filter { it.word.id != asked.id }
            .distinctBy { it.word.id }

    companion object {
        private val brackets = Regex("""\([^)]*\)|\[[^\]]*\]""")
        private val germanPlaceholders = Regex(
            """(?<!\p{L})(jemanden|jemandem|jemandes|jemand|etwas|jdn|jdm|jds|jn|jm|js|etw)\.?(?!\p{L})"""
        )
        private val englishPlaceholders = Regex(
            """(?<!\p{L})(sb's|sth's|one's|somebody|someone|something|sb|sth)\.?(?!\p{L})"""
        )
        private val punctuation = Regex("[,;.!?]")

        /** German meaning without gender endings, bracketed parts and placeholders, normalized. */
        internal fun comparableMeaning(meaning: String): String {
            val text = GermanText.withoutGenderSuffix(meaning).lowercase()
                .replace(brackets, " ")
                .replace(germanPlaceholders, " ")
                .replace(punctuation, " ")
            return normalizeText(text)
        }

        /** What typing [word] means: its core word (every active vocabulary entry has one); null for verbs. */
        internal fun typedTarget(word: QuizWord): String? =
            word.coreWord?.let { AnswerChecker.typedKey(it).takeIf(String::isNotEmpty) }

        /** An English text in lower case without bracketed parts and placeholders (sb., sth., …). */
        internal fun withoutBracketsAndPlaceholders(english: String): String =
            english.lowercase().replace(brackets, " ").replace(englishPlaceholders, " ")
    }
}
