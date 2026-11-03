package io.github.markushornbach.voctrainer.learning

/** The parts of a vocabulary entry the quiz logic needs, independent of Room. */
data class QuizWord(
    val id: String,
    val english: String,
    /** Ordered; the first element is the main meaning. */
    val germanMeanings: List<String>,
    val unit: Int?,
    val part: String?,
    val leitnerLevel: Int,
    val page: Int? = null,
    val storedType: WordType? = null,
    /** Only set for irregular verbs. */
    val verbForms: VerbForms? = null,
    /** Last first-attempt answer in a round (epoch millis); null = never since reviews are recorded. */
    val lastReviewedAt: Long? = null,
    /** Word to type in a later question type. See [io.github.markushornbach.voctrainer.data.Vocabulary.coreWord]. */
    val coreWord: String? = null
) {
    val mainMeaning: String get() = germanMeanings.first()

    val effectiveType: WordType by lazy { WordType.effective(storedType, english, mainMeaning) }

    /**
     * Can be typed: a vocabulary entry with a core word, which every active entry has; phrases are
     * typed like words (the core word fills the gap). Multiple choice raises such a word only up
     * to [Leitner.CHOICE_MAX_TYPEABLE]. Irregular verbs are never typeable here (they have no
     * core word and their own quiz).
     */
    val isTypeable: Boolean
        get() = coreWord != null && verbForms == null

    // Comparison keys for building distractors, computed once per word: the regex-based
    // normalization is far too slow to repeat for every pair of words at the start of a round.

    /** English text, normalized. */
    internal val englishKey: String by lazy { normalizeText(english) }

    /** All German meanings as displayed ("Tänzer/in" = "Tänzer"), normalized. */
    internal val meaningKeys: Set<String> by lazy { germanMeanings.mapTo(HashSet(), ::meaningKey) }

    /** The main meaning as displayed, normalized. */
    internal val mainMeaningKey: String by lazy { meaningKey(mainMeaning) }

    /** Regular verbs mixed into verb rounds count for the round only, never for progress. */
    val countsForProgress: Boolean get() = verbForms?.isRegular != true
}

/**
 * Forms of a verb for the verb quiz: as imported for irregular verbs (e.g. "was / were"), or
 * computed for a regular verb taken from the vocabulary ([isRegular]).
 */
data class VerbForms(val simplePast: String, val pastParticiple: String?, val isRegular: Boolean = false)

private val whitespace = Regex("\\s+")

internal fun normalizeText(text: String): String = text.trim().lowercase().replace(whitespace, " ")

/** A German meaning as displayed, normalized; "Tänzer/in" and "Tänzer" give the same key. */
internal fun meaningKey(meaning: String): String = normalizeText(GermanText.withoutGenderSuffix(meaning))
