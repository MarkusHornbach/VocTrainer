package io.github.markushornbach.voctrainer.learning

sealed interface UnitChoice {
    data object All : UnitChoice
    data class Number(val unit: Int) : UnitChoice
    data object WithoutUnit : UnitChoice
}

/** The selection of the quiz start screen (per quiz mode); the filters are combined with AND. */
data class QuizFilter(
    val unit: UnitChoice = UnitChoice.All,
    /** Selected parts of the unit (`null` = "Einstieg"); empty means all parts. */
    val parts: Set<String?> = emptySet(),
    /** null = all pages. */
    val pageFrom: Int? = null,
    /** null = only [pageFrom]. */
    val pageTo: Int? = null
)

/** Selection logic for the quiz start screen. [words] must be in insertion (textbook) order. */
object QuizSelection {
    fun unitChoices(words: List<QuizWord>): List<UnitChoice> =
        listOf(UnitChoice.All) +
            words.mapNotNull { it.unit }.distinct().sorted().map { UnitChoice.Number(it) } +
            if (words.any { it.unit == null }) listOf(UnitChoice.WithoutUnit) else emptyList()

    /** Parts of the selected unit in textbook order, "Einstieg" (null) first; empty for "Alle Units". */
    fun partOptions(words: List<QuizWord>, unit: UnitChoice): List<String?> {
        if (unit == UnitChoice.All) return emptyList()
        val parts = words.filter { matchesUnit(it, unit) }.map { it.part }.distinct()
        return parts.filter { it == null } + parts.filter { it != null }
    }

    /**
     * Pages of the words matching the current Unit/Part selection, ascending: only these can be chosen,
     * so the selection never becomes empty through Unit, Part or Seiten.
     */
    fun availablePages(words: List<QuizWord>, filter: QuizFilter): List<Int> =
        words.filter { matchesUnitAndParts(it, filter) }.mapNotNull { it.page }.distinct().sorted()

    /**
     * The words of the selection ("Auswahl"), phrases included. Each word must match Unit, Part and
     * Seiten itself: on a page shared by two parts only the words of the selected parts count.
     */
    fun filter(words: List<QuizWord>, filter: QuizFilter): List<QuizWord> {
        val from = filter.pageFrom
        val pages = from?.let { it..(filter.pageTo ?: it) }
        return words.filter { word ->
            matchesUnitAndParts(word, filter) &&
                (pages == null || (word.page != null && word.page in pages))
        }
    }

    /** Drops selections that no longer exist (e.g. after the unit changed or words were deleted). */
    fun normalize(words: List<QuizWord>, filter: QuizFilter): QuizFilter {
        val unit = filter.unit.takeIf { it in unitChoices(words) } ?: UnitChoice.All
        val parts = filter.parts.intersect(partOptions(words, unit).toSet())
        val withParts = filter.copy(unit = unit, parts = parts)
        val from = filter.pageFrom
        val to = filter.pageTo?.takeIf { from != null && it >= from }
        // A range stays as long as it still covers a page of the selection; the endpoints themselves
        // need not exist there (e.g. 201–203 while only "Einstieg" on 201–202 is selected).
        val stillCovered = from != null && availablePages(words, withParts).any { it in from..(to ?: from) }
        return if (stillCovered) withParts.copy(pageFrom = from, pageTo = to) else withParts.copy(pageFrom = null, pageTo = null)
    }

    /** True if [draft] selects something else than [snapshot] (both are normalized first). */
    fun isChanged(words: List<QuizWord>, snapshot: QuizFilter, draft: QuizFilter): Boolean =
        normalize(words, snapshot) != normalize(words, draft)

    // Changes of the filter UI, shared by the quiz start screen and the vocabulary editor. The levels
    // depend on each other in one direction only: Unit → Parts → Seiten.

    /** The unit is the top level: Parts and Seiten go back to "Alle". */
    fun withUnit(filter: QuizFilter, unit: UnitChoice): QuizFilter =
        filter.copy(unit = unit, parts = emptySet(), pageFrom = null, pageTo = null)

    /**
     * Toggles one part; `null` is "Einstieg". An empty result means "Alle", and so does a selection
     * that covers every one of [allParts] (collapsed, so "Alle" is the only way to say "everything").
     * Seiten go back to "Alle" (all pages of the selected parts).
     */
    fun togglePart(filter: QuizFilter, part: String?, allParts: List<String?> = emptyList()): QuizFilter {
        val parts = if (part in filter.parts) filter.parts - part else filter.parts + part
        return withParts(filter, if (allParts.isNotEmpty() && parts.containsAll(allParts)) emptySet() else parts)
    }

    /** The "Alle" chip of the parts; Seiten go back to "Alle" like for every change of the parts. */
    fun withAllParts(filter: QuizFilter): QuizFilter = withParts(filter, emptySet())

    private fun withParts(filter: QuizFilter, parts: Set<String?>): QuizFilter =
        filter.copy(parts = parts, pageFrom = null, pageTo = null)

    /** Seiten never change Unit or Parts. */
    fun withPageFrom(filter: QuizFilter, page: Int?): QuizFilter =
        filter.copy(pageFrom = page, pageTo = filter.pageTo?.takeIf { to -> page != null && to >= page })

    private fun matchesUnitAndParts(word: QuizWord, filter: QuizFilter): Boolean =
        matchesUnit(word, filter.unit) && (filter.parts.isEmpty() || word.part in filter.parts)

    private fun matchesUnit(word: QuizWord, unit: UnitChoice): Boolean = when (unit) {
        UnitChoice.All -> true
        is UnitChoice.Number -> word.unit == unit.unit
        UnitChoice.WithoutUnit -> word.unit == null
    }
}
