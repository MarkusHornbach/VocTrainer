package io.github.markushornbach.voctrainer.learning

/**
 * The letters that differ between the learner's input and the correct spelling: indices into
 * [given] and [expected]; an empty range means nothing to mark on that side (a missing letter is
 * marked in the correct spelling only, an extra letter in the input only).
 */
data class SpellingDiff(val given: IntRange, val expected: IntRange) {
    companion object {
        /** The part between the common prefix and the common suffix (American spelling). */
        fun around(given: String, expected: String): SpellingDiff {
            val prefix = given.commonPrefixWith(expected).length
            val maxSuffix = minOf(given.length, expected.length) - prefix
            val suffix = given.commonSuffixWith(expected).length.coerceAtMost(maxSuffix)
            return SpellingDiff(prefix until given.length - suffix, prefix until expected.length - suffix)
        }
    }
}

/** "Fast richtig – Tippfehler": exactly one edit (optimal string alignment distance 1). */
object Typo {
    /** Shorter targets never count a typo ("cut" for "cat" stays wrong). */
    const val MIN_LETTERS = 4

    fun longEnough(target: String): Boolean = target.count(Char::isLetter) >= MIN_LETTERS

    /**
     * [given] is one edit away from [expected]: one letter substituted, missing or extra, or two
     * adjacent letters swapped. Returns the differing letters, null otherwise (also if equal).
     */
    fun diff(given: String, expected: String): SpellingDiff? {
        if (given == expected) return null
        val at = given.commonPrefixWith(expected).length
        return when (given.length - expected.length) {
            0 -> when {
                given.substring(at + 1) == expected.substring(at + 1) -> SpellingDiff(at..at, at..at)
                at + 1 < given.length && given[at] == expected[at + 1] && given[at + 1] == expected[at] &&
                    given.substring(at + 2) == expected.substring(at + 2) -> SpellingDiff(at..at + 1, at..at + 1)
                else -> null
            }
            1 -> if (given.removeRange(at, at + 1) == expected) SpellingDiff(at..at, IntRange.EMPTY) else null
            -1 -> if (expected.removeRange(at, at + 1) == given) SpellingDiff(IntRange.EMPTY, at..at) else null
            else -> null
        }
    }
}

/**
 * "Fast richtig – amerikanisch": American variants of a British spelling, made by rules (no word
 * list). Variants that differ by one letter only (grey/gray) are found by [Typo] anyway.
 */
object AmericanSpelling {
    private class Rule(val name: String, val regex: Regex, val replace: (MatchResult) -> String?)

    private const val START = """(?<!\p{L})"""
    private const val END = """(?!\p{L})"""

    /** Words that are never spelt with -ize (base form; compounds of "wise" are found by their ending). */
    private val NEVER_IZE = setOf(
        "rise", "arise", "sunrise", "noise", "raise", "praise", "cruise", "poise", "exercise", "surprise",
        "promise", "advertise", "advise", "revise", "supervise", "compromise", "enterprise", "paradise",
        "premise", "televise", "despise", "disguise", "devise", "improvise", "merchandise", "franchise",
        "chastise", "expertise", "precise", "concise", "practise", "treatise", "tortoise", "porpoise",
        "bruise", "guise", "demise", "reprise", "excise", "incise", "circumcise", "valise", "chemise", "anise"
    )

    private val vowel = Regex("[aeiouy]")

    private val RULES = listOf(
        // colour, favourite, neighbours, honourable; not journey, course, four, tourist.
        Rule("our → or", Regex("""$START(\p{L}{2,})our(s|ed|ing|ite|ites|ful|able|er|ers|ist|less)?$END""")) {
            it.groupValues[1] + "or" + it.groupValues[2]
        },
        // centre, theatre, metre, litre, fibre.
        Rule("tre → ter", Regex("""$START(\p{L}+)([tb])re(s?)$END""")) {
            it.groupValues[1] + it.groupValues[2] + "er" + it.groupValues[3]
        },
        // organise, realised, organisation, analyse.
        Rule("ise → ize", Regex("""$START(\p{L}+?)([iy])s(e|ed|es|ing|er|ers|ation|ations)$END""")) {
            val base = it.groupValues[1] + it.groupValues[2] + "se"
            if (base in NEVER_IZE || base.endsWith("wise")) null
            else it.groupValues[1] + it.groupValues[2] + "z" + it.groupValues[3]
        },
        // travelled, cancelling, modelling, traveller; not filled, called, spelling, smaller, seller.
        Rule("ll → l", Regex("""$START(\p{L}{3,})([aeiou])ll(ed|ing|er|ers|ery)$END""")) {
            if (!vowel.containsMatchIn(it.groupValues[1])) null
            else it.groupValues[1] + it.groupValues[2] + "l" + it.groupValues[3]
        },
        // dialogue, catalogue (at least 7 letters).
        Rule("logue → log", Regex("""$START(\p{L}{2,})logue$END""")) { it.groupValues[1] + "log" },
        // programme.
        Rule("gramme → gram", Regex("""$START(\p{L}*)gramme(s?)$END""")) {
            it.groupValues[1] + "gram" + it.groupValues[2]
        },
        // defence, offence, licence, pretence, also inside longer words (defenceless).
        Rule("ce → se", Regex("""(defen|offen|licen|preten)ce""")) { it.groupValues[1] + "se" }
    )

    private fun Rule.apply(text: String): String = regex.replace(text) { replace(it) ?: it.value }

    /** Every American variant of [british] (any combination of the rules) with the rules applied. */
    fun variants(british: String): Map<String, List<String>> {
        var all = listOf(british to emptyList<String>())
        for (rule in RULES) {
            all = all + all.mapNotNull { (text, rules) ->
                rule.apply(text).takeIf { it != text }?.let { it to rules + rule.name }
            }
        }
        return all.drop(1).toMap()
    }
}

/**
 * Every word of the learning material: core words, the single words of all English entries
 * (brackets and placeholders removed) and all forms of the irregular verbs. A typo or an American
 * variant that is itself one of these words is wrong ("cut" for "cat"). Built once per round,
 * like [AlsoCorrectIndex].
 */
class LearningMaterial(vocabulary: List<QuizWord>, verbs: List<QuizWord> = emptyList()) {
    private val words: Set<String> = buildSet {
        for (word in vocabulary) {
            word.coreWord?.let { addAll(singleWords(it)) }
            addAll(singleWords(word.english))
        }
        for (verb in verbs) {
            addAll(singleWords(verb.english))
            verb.verbForms?.let { forms ->
                addAll(singleWords(forms.simplePast))
                forms.pastParticiple?.let { addAll(singleWords(it)) }
            }
        }
    }

    operator fun contains(word: String): Boolean = AnswerChecker.typedKey(word) in words

    companion object {
        val EMPTY = LearningMaterial(emptyList())

        private val separators = Regex("""[^\p{L}'\-]+""")

        internal fun singleWords(text: String): List<String> =
            AnswerChecker.typedKey(AlsoCorrectIndex.withoutBracketsAndPlaceholders(text))
                .split(separators)
                .map { it.trim('\'', '-') }
                .filter(String::isNotEmpty)
    }
}
