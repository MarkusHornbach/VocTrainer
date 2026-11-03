package io.github.markushornbach.voctrainer.learning

import kotlin.random.Random

/**
 * Which forms a verb question asks: the simple past ("2 Formen"), or the simple past and then the
 * past participle in a second step ("3 Formen").
 */
enum class VerbQuestion { SIMPLE_PAST, THREE_FORMS }

/**
 * Questions for irregular verbs: "(to) take" → took / taked / toke / tought.
 * Distractors are built by fixed rules from the verb itself (regular form, vowel change,
 * typical endings), with other verbs' simple past as a fallback. Only the button order and the
 * fallback choice are random. With [VerbQuestion.THREE_FORMS] only verbs with a past participle
 * get a question; its second step asks the participle ([participleDistractors]).
 */
class VerbDistractorGenerator(
    private val random: Random,
    private val question: VerbQuestion = VerbQuestion.SIMPLE_PAST
) : QuestionGenerator {

    override fun generate(target: QuizWord, candidates: List<QuizWord>): MultipleChoiceQuestion? {
        val forms = target.verbForms ?: return null
        val correct = forms.simplePast
        val distractors = distractors(target, candidates)
        if (distractors.size < QuestionGenerator.DISTRACTOR_COUNT) return null
        val (options, correctIndex) = shuffled(correct, distractors)
        val subtitle = GermanText.withoutGenderSuffix(target.mainMeaning)
        if (question == VerbQuestion.SIMPLE_PAST) {
            return MultipleChoiceQuestion(
                word = target,
                prompt = target.english,
                options = options,
                correctIndex = correctIndex,
                subtitle = subtitle,
                instruction = "Wie lautet das simple past?",
                solution = "${target.english} – $correct",
                speakOnShow = target.english,
                speakAfterAnswer = correct
            )
        }
        val participle = forms.pastParticiple ?: return null
        val second = participleDistractors(target, candidates)
        if (second.size < QuestionGenerator.DISTRACTOR_COUNT) return null
        val (secondOptions, secondIndex) = shuffled(participle, second)
        val allForms = "${target.english} – $correct – $participle"
        return MultipleChoiceQuestion(
            word = target,
            prompt = target.english,
            options = options,
            correctIndex = correctIndex,
            subtitle = subtitle,
            instruction = "Wie lauten die Formen?",
            solution = "${target.english} – $correct",
            speakOnShow = target.english,
            // Nothing after the simple past; all three forms in one go after the participle.
            speakAfterAnswer = null,
            secondStep = AnswerStep(secondOptions, secondIndex, solution = allForms, speakAfterAnswer = allForms)
        )
    }

    override fun canGenerate(target: QuizWord, candidates: List<QuizWord>): Boolean {
        val forms = target.verbForms ?: return false
        if (distractors(target, candidates).size < QuestionGenerator.DISTRACTOR_COUNT) return false
        return question == VerbQuestion.SIMPLE_PAST ||
            (forms.pastParticiple != null &&
                participleDistractors(target, candidates).size >= QuestionGenerator.DISTRACTOR_COUNT)
    }

    /** The options in random order and the index of [correct] among them. */
    private fun shuffled(correct: String, distractors: List<String>): Pair<List<String>, Int> {
        val options = (distractors.map { it to false } + (correct to true)).shuffled(random)
        return options.map { it.first } to options.indexOfFirst { it.second }
    }

    private fun answerOf(word: QuizWord): String? = word.verbForms?.simplePast

    /** Up to three simple past distractors for [target], with the infinitive's tail ("a bike", "up") appended. */
    fun distractors(target: QuizWord, candidates: List<QuizWord>): List<String> {
        val answer = answerOf(target) ?: return emptyList()
        if (target.verbForms?.isRegular == true) return regularDistractors(target, answer, candidates)
        val (head, tail) = splitHead(target.english)
        val ownForms = buildSet {
            add(head)
            addAll(correctHeads(answer))
            addAll(IrregularVerbList.pastForms[head].orEmpty())
        }
        val realForms = realForms(candidates)

        val chosen = mutableListOf<String>()
        fun accept(candidate: String, checkRealForm: Boolean = true): Boolean {
            val valid = chosen.size < QuestionGenerator.DISTRACTOR_COUNT &&
                candidate.length >= 3 &&
                candidate !in ownForms &&
                candidate !in IrregularVerbList.forbidden &&
                candidate !in chosen &&
                !(checkRealForm && candidate in realForms)
            if (valid) chosen += candidate
            return valid
        }

        accept(regularForm(head))
        vowelChanges(head).firstOrNull { accept(it) }
        val parts = parse(head)
        if (parts != null) {
            tEnding(head, parts)?.let { accept(it) }
            if (parts.prefix.isNotEmpty()) {
                listOf(parts.prefix + "ought", parts.prefix + "aught").firstOrNull { accept(it) }
            }
        }

        if (chosen.size < QuestionGenerator.DISTRACTOR_COUNT) {
            val others = candidates.filter { it.id != target.id && answerOf(it) != null }
            val (sameUnit, otherUnits) = others.partition { it.unit == target.unit }
            for (verb in sameUnit.shuffled(random) + otherUnits.shuffled(random)) {
                if (chosen.size == QuestionGenerator.DISTRACTOR_COUNT) break
                accept(correctHeads(answerOf(verb)!!).first(), checkRealForm = false)
            }
        }
        return chosen.map { it + tail }
    }

    /**
     * Up to three past participle distractors for [target] (second step of [VerbQuestion.THREE_FORMS]):
     * the verb's own simple past as stored if it differs from the participle (went for gone), then
     * fixed rules: regular form (goed), "-en"/"-n" on the simple past (tooken) and on the infinitive
     * (goen), "-ed" on the simple past (wented), vowel change and "-t" ending, with the infinitive's
     * tail appended. Never a form of another verb (built-in list or imported, participles included)
     * and never the correct answer. A regular verb gets the distractors of its simple past (the same
     * "-ed" form in both steps).
     */
    fun participleDistractors(target: QuizWord, candidates: List<QuizWord>): List<String> {
        val forms = target.verbForms ?: return emptyList()
        val participle = forms.pastParticiple ?: return emptyList()
        if (forms.isRegular) return regularDistractors(target, participle, candidates)
        val (head, tail) = splitHead(target.english)
        val participleHeads = correctHeads(participle)
        val pastHeads = correctHeads(forms.simplePast)
        // "was / were" for "been" is one form; made / made or put / put / put give no own distractor.
        val ownPast = forms.simplePast.trim().takeIf { pastHeads.none { it in participleHeads } }
        val ownForms = buildSet {
            add(head)
            addAll(participleHeads)
            addAll(pastHeads)
            addAll(IrregularVerbList.pastForms[head].orEmpty())
        }
        val realForms = participleRealForms(candidates)
        val pastHead = pastHeads.firstOrNull() ?: head
        val rules = buildList {
            add(regularForm(head))
            add(enForm(pastHead))
            add(enForm(head))
            add(regularForm(pastHead))
            addAll(vowelChanges(head))
            parse(head)?.let { parts -> tEnding(head, parts)?.let(::add) }
        }
        val chosen = rules.filter { candidate ->
            candidate.length >= 3 &&
                candidate !in ownForms &&
                candidate !in realForms &&
                candidate !in IrregularVerbList.forbidden
        }.distinct().take(QuestionGenerator.DISTRACTOR_COUNT - if (ownPast != null) 1 else 0)
        return listOfNotNull(ownPast) + chosen.map { it + tail }
    }

    /** "-en" participle: take → taken, took → tooken, go → goen. */
    private fun enForm(word: String): String = if (word.endsWith("e")) word + "n" else word + "en"

    // The pool stays the same for a whole round, so these forms are only collected once.
    @Volatile
    private var participleFormsCache: Pair<List<QuizWord>, Set<String>>? = null

    /** [realForms] plus the past participles of all imported irregular verbs. */
    private fun participleRealForms(candidates: List<QuizWord>): Set<String> {
        participleFormsCache?.let { (pool, forms) -> if (pool === candidates) return forms }
        val forms = HashSet(realForms(candidates))
        candidates.forEach { verb ->
            if (verb.verbForms?.isRegular == true) return@forEach
            verb.verbForms?.pastParticiple?.let { forms += correctHeads(it) }
        }
        return forms.also { participleFormsCache = candidates to it }
    }

    /** Base and simple past forms of the built-in list and of all imported irregular verbs. */
    // The pool stays the same for a whole round, so its real forms are only collected once.
    @Volatile
    private var realFormsCache: Pair<List<QuizWord>, Set<String>>? = null

    private fun realForms(candidates: List<QuizWord>): Set<String> {
        realFormsCache?.let { (pool, forms) -> if (pool === candidates) return forms }
        return collectRealForms(candidates).also { realFormsCache = candidates to it }
    }

    private fun collectRealForms(candidates: List<QuizWord>): Set<String> {
        val forms = HashSet(IrregularVerbList.allForms)
        candidates.forEach { verb ->
            if (verb.verbForms?.isRegular == true) return@forEach
            forms += splitHead(verb.english).first
            answerOf(verb)?.let { forms += correctHeads(it) }
        }
        return forms
    }

    /**
     * Distractors for a regular verb ("shopped"): irregular-looking forms (vowel change, "-t",
     * "-ought"/"-aught") plus one typical spelling mistake ("shoped", "danceed"); "-t" ("helpt")
     * as a last resort. No fallback to other verbs.
     */
    private fun regularDistractors(target: QuizWord, answer: String, candidates: List<QuizWord>): List<String> {
        val (head, tail) = splitHead(target.english)
        val correct = correctHeads(answer).first()
        val ownForms = setOf(head, correct)
        val realForms = realForms(candidates)
        fun valid(candidate: String) = candidate.length >= 3 &&
            candidate !in ownForms &&
            candidate !in realForms &&
            candidate !in IrregularVerbList.forbidden

        val irregularLooking = mutableListOf<String>()
        vowelChanges(head).firstOrNull { valid(it) }?.let { irregularLooking += it }
        parse(head)?.let { parts ->
            tEnding(head, parts)?.takeIf { valid(it) && it !in irregularLooking }?.let { irregularLooking += it }
            if (parts.prefix.isNotEmpty()) {
                listOf(parts.prefix + "ought", parts.prefix + "aught")
                    .firstOrNull { valid(it) && it !in irregularLooking }
                    ?.let { irregularLooking += it }
            }
        }

        val spelling = listOfNotNull(spellingMistake(head, correct), yMistake(head))
            .firstOrNull { valid(it) && it !in irregularLooking }
        val chosen = irregularLooking.take(QuestionGenerator.DISTRACTOR_COUNT - if (spelling != null) 1 else 0) +
            listOfNotNull(spelling)
        val withT = if (chosen.size < QuestionGenerator.DISTRACTOR_COUNT && !head.endsWith("e")) {
            chosen + listOfNotNull((head + "t").takeIf { valid(it) && it !in chosen })
        } else {
            chosen
        }
        return withT.map { it + tail }
    }

    /** First applicable spelling rule: shopped → shoped, dance → danceed, listen → listenned. */
    private fun spellingMistake(head: String, correct: String): String? {
        val n = correct.length
        return when {
            n >= 4 && correct.endsWith("ed") && correct[n - 3] == correct[n - 4] && correct[n - 3] !in VOWELS_Y ->
                head + "ed"
            head.endsWith("e") -> head + "ed"
            head.length >= 2 && head.last() in "bdglmnprt" && head[head.length - 2] in VOWELS ->
                head + head.last() + "ed"
            head.length >= 2 && head.last() == 'y' && head[head.length - 2] !in VOWELS_Y -> head + "ed"
            else -> null
        }
    }

    /** stay → staied (used when the spelling rule gives nothing valid). */
    private fun yMistake(head: String): String? =
        if (head.length >= 2 && head.last() == 'y' && head[head.length - 2] in VOWELS) head.dropLast(1) + "ied" else null

    private class Parts(val prefix: String, val vowel: String, val coda: String, val finalE: Boolean)

    private fun parse(word: String): Parts? {
        val match = PARSE.find(word) ?: return null
        return Parts(
            prefix = word.substring(0, match.range.first),
            vowel = match.groupValues[1],
            coda = match.groupValues[2],
            finalE = match.groupValues[3].isNotEmpty()
        )
    }

    /** B. Vowel change candidates in table order: think → thank, take → toke. */
    private fun vowelChanges(word: String): List<String> {
        val parts = parse(word) ?: return emptyList()
        val (key, prefix) = if (word.endsWith("ear")) {
            "ea_r" to word.dropLast(3)
        } else {
            (parts.vowel + if (parts.finalE) "_e" else "") to parts.prefix
        }
        return VOWEL_TABLE[key].orEmpty().map { replacement ->
            when {
                replacement == "ought" || replacement == "aid" -> prefix + replacement
                replacement == "o_re" -> prefix + "ore"
                replacement.endsWith("_e") -> prefix + replacement.removeSuffix("_e") + parts.coda + "e"
                else -> prefix + replacement + parts.coda
            }
        }
    }

    /** "-t" ending like feel → felt: mean → ment, feed → fent, win → wint, find → fint. */
    private fun tEnding(word: String, parts: Parts): String? = when {
        (parts.vowel == "ee" || parts.vowel == "ea") && parts.coda in setOf("l", "n", "d", "m", "") ->
            if (parts.coda == "d") parts.prefix + "ent" else parts.prefix + "e" + parts.coda + "t"
        parts.coda in setOf("l", "n", "nd") && !parts.finalE ->
            (if (parts.coda == "nd") word.dropLast(1) else word) + "t"
        else -> null
    }

    companion object {
        private const val VOWELS = "aeiou"
        private const val VOWELS_Y = "aeiouy"
        private val PARSE = Regex("([aeiouy]+)([^aeiouy]*)(e?)$")
        private val VOWEL_GROUPS = Regex("[aeiouy]+")
        private val VOWEL_TABLE = mapOf(
            "i" to listOf("a", "u", "o"),
            "ie" to listOf("o"),
            "i_e" to listOf("o_e", "i", "a_e"),
            "e" to listOf("o", "a"),
            "ee" to listOf("e", "o"),
            "ea" to listOf("e", "o", "o_e"),
            "a" to listOf("u", "oo", "e"),
            "a_e" to listOf("o_e", "oo"),
            "o" to listOf("e", "a", "u"),
            "o_e" to listOf("a_e", "u"),
            "oo" to listOf("o_e", "o"),
            "ay" to listOf("aid", "ew"),
            "uy" to listOf("ought"),
            "u" to listOf("a", "o"),
            "ea_r" to listOf("o_re")
        )

        /** "(to) ride a bike" → ("ride", " a bike"); the head word in lower case, the tail as stored. */
        fun splitHead(text: String): Pair<String, String> {
            var words = text.trim()
            if (words.lowercase().startsWith("(to) ")) words = words.substring(5).trim()
            val space = words.indexOf(' ')
            return if (space < 0) {
                words.lowercase() to ""
            } else {
                words.substring(0, space).lowercase() to words.substring(space)
            }
        }

        /** A. Regular past: take → taked, swim → swimmed, forget → forgetted, go → goed. */
        fun regularForm(word: String): String {
            val last = word.last()
            return when {
                last == 'e' -> word + "d"
                last == 'y' && word.length >= 2 && word[word.length - 2] !in VOWELS_Y -> word.dropLast(1) + "ied"
                isShortClosed(word) -> word + last + "ed"
                word.endsWith("get") -> word + "ted"
                else -> word + "ed"
            }
        }

        /** One vowel group, ending in a single vowel + consonant (not w, x, y). */
        private fun isShortClosed(word: String): Boolean {
            if (word.length < 2 || VOWEL_GROUPS.findAll(word).count() != 1) return false
            val last = word.last()
            val vowel = word[word.length - 2]
            val beforeVowel = word.getOrNull(word.length - 3)
            return last !in VOWELS_Y && last !in "wx" && vowel in VOWELS && (beforeVowel == null || beforeVowel !in VOWELS_Y)
        }

        /** "was / were" → [was, were]; head word of each form. */
        fun correctHeads(simplePast: String): List<String> =
            simplePast.split("/").map { splitHead(it).first }.filter { it.isNotEmpty() }
    }
}
