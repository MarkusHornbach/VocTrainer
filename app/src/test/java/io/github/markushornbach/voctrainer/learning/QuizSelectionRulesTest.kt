package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of the selection screen (Unit → Parts → Seiten), checked for every combination and in both
 * modes: the vocabulary and the irregular verbs use the same selection logic.
 */
class QuizSelectionRulesTest {
    /**
     * Unit 1: "Einstieg" on 10–11, Part A on 11–13, Part B on 13–14 (pages 11 and 13 are shared by two
     * parts). Unit 2: Part A on 20–21, Part B on 21–22. One entry without a page in unit 2, Part B.
     */
    private fun material(type: WordType?): List<QuizWord> {
        var n = 0
        fun w(unit: Int, part: String?, page: Int?) =
            word("${type ?: "v"}${n++}", "e$n", "g$n", unit = unit, part = part, page = page, type = type)
        return listOf(
            w(1, null, 10), w(1, null, 11),
            w(1, "A", 11), w(1, "A", 12), w(1, "A", 13),
            w(1, "B", 13), w(1, "B", 14), w(1, "B", 14),
            w(2, "A", 20), w(2, "A", 21),
            w(2, "B", 21), w(2, "B", 22), w(2, "B", null)
        )
    }

    private val vocabulary = material(null)
    private val verbs = material(WordType.VERB)
    private val modes = listOf(vocabulary, verbs)

    private fun pagesOf(words: List<QuizWord>, unit: Int, vararg parts: String?) =
        words.filter { it.unit == unit && it.part in parts }.mapNotNull { it.page }.toSet()

    /** Every non-empty subset of [options]; the full set is stored as "Alle" (empty), like the UI does. */
    private fun partSelections(options: List<String?>): List<Set<String?>> {
        val subsets = (1 until (1 shl options.size)).map { mask ->
            options.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
        }
        return listOf(emptySet<String?>()) + subsets.filter { it.size < options.size }
    }

    private fun matches(word: QuizWord, filter: QuizFilter): Boolean {
        val unitOk = when (val u = filter.unit) {
            UnitChoice.All -> true
            is UnitChoice.Number -> word.unit == u.unit
            UnitChoice.WithoutUnit -> word.unit == null
        }
        val partOk = filter.parts.isEmpty() || word.part in filter.parts
        val from = filter.pageFrom
        val pageOk = from == null || (word.page != null && word.page in from..(filter.pageTo ?: from))
        return unitOk && partOk && pageOk
    }

    /** All filters the screen can produce: Unit, then Parts (not for "Alle Units"), then one page or a range. */
    private fun reachableFilters(words: List<QuizWord>): List<QuizFilter> =
        QuizSelection.unitChoices(words).flatMap { unit ->
            val withUnit = QuizSelection.withUnit(QuizFilter(), unit)
            val partSets = if (unit == UnitChoice.All) listOf(emptySet()) else partSelections(QuizSelection.partOptions(words, unit))
            partSets.flatMap { parts ->
                val withParts = withUnit.copy(parts = parts)
                val pages = QuizSelection.availablePages(words, withParts)
                listOf(withParts) + pages.flatMap { from ->
                    val single = QuizSelection.withPageFrom(withParts, from)
                    listOf(single) + pages.filter { it > from }.map { single.copy(pageTo = it) }
                }
            }
        }

    // Rule 1: Unit is the top level.

    @Test
    fun changingTheUnitResetsPartsAndPages() = modes.forEach { words ->
        val narrowed = QuizSelection.withPageFrom(
            QuizSelection.togglePart(QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1)), "A"), 12
        ).copy(pageTo = 13)
        for (unit in listOf(UnitChoice.Number(2), UnitChoice.Number(1), UnitChoice.All)) {
            val changed = QuizSelection.withUnit(narrowed, unit)
            assertEquals(QuizFilter(unit = unit), changed)
        }
        assertEquals(words.size, QuizSelection.filter(words, QuizSelection.withUnit(narrowed, UnitChoice.All)).size)
    }

    // Rule 2: "Alle Units" offers no parts; narrowing only via Seiten.

    @Test
    fun alleUnitsOffersNoPartsButAllPages() = modes.forEach { words ->
        assertEquals(emptyList<String?>(), QuizSelection.partOptions(words, UnitChoice.All))
        assertEquals(listOf(10, 11, 12, 13, 14, 20, 21, 22), QuizSelection.availablePages(words, QuizFilter()))
        // A stored part set under "Alle Units" is dropped.
        assertEquals(emptySet<String?>(), QuizSelection.normalize(words, QuizFilter(parts = setOf("A"))).parts)
        val page13 = QuizSelection.withPageFrom(QuizFilter(), 13)
        assertEquals(2, QuizSelection.filter(words, page13).size)
        val range = page13.copy(pageTo = 21)
        assertEquals(words.filter { it.page in 13..21 }, QuizSelection.filter(words, range))
    }

    // Rule 3: a change of the parts resets the pages.

    @Test
    fun changingThePartsResetsThePages() {
        val unit1 = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1))
        val allParts = QuizSelection.partOptions(vocabulary, UnitChoice.Number(1))
        val partA = QuizSelection.togglePart(unit1, "A", allParts)
        val paged = QuizSelection.withPageFrom(partA, 12).copy(pageTo = 13)

        val plusB = QuizSelection.togglePart(paged, "B", allParts)
        assertEquals(setOf<String?>("A", "B"), plusB.parts)
        assertNull(plusB.pageFrom)
        assertNull(plusB.pageTo)

        val withoutA = QuizSelection.togglePart(paged, "A", allParts)
        assertEquals(emptySet<String?>(), withoutA.parts)
        assertNull(withoutA.pageFrom)

        val alle = QuizSelection.withAllParts(paged)
        assertEquals(QuizFilter(unit = UnitChoice.Number(1)), alle)

        // "Alle Seiten" then means all pages of the selected parts.
        assertEquals(listOf(11, 12, 13, 14), QuizSelection.availablePages(vocabulary, plusB))
    }

    @Test
    fun tickingPartsOneByOneOnTheTextbookExampleResetsTheRange() {
        val example = exampleWords(EXAMPLE_BOOK)
        val allParts = QuizSelection.partOptions(example, UnitChoice.Number(1))
        var filter = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1))
        filter = QuizSelection.togglePart(filter, null, allParts)
        filter = QuizSelection.withPageFrom(filter, 150).copy(pageTo = 151)
        filter = QuizSelection.togglePart(filter, "A", allParts)
        assertNull(filter.pageFrom)
        // "Einstieg" + Part A on all their pages: page 150 to the middle of 152.
        assertEquals(30, QuizSelection.filter(example, filter).size)
        assertEquals(listOf(150, 151, 152), QuizSelection.availablePages(example, filter))
    }

    // Rule 4: only pages with matching entries are offered.

    @Test
    fun offeredPagesAreExactlyThePagesOfTheUnitAndParts() = modes.forEach { words ->
        val unit1 = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1))
        val unit2 = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(2))
        assertEquals(listOf(10, 11, 12, 13, 14), QuizSelection.availablePages(words, unit1))
        assertEquals(listOf(20, 21, 22), QuizSelection.availablePages(words, unit2))
        assertEquals(listOf(10, 11), QuizSelection.availablePages(words, unit1.copy(parts = setOf(null))))
        assertEquals(listOf(11, 12, 13), QuizSelection.availablePages(words, unit1.copy(parts = setOf("A"))))
        assertEquals(listOf(13, 14), QuizSelection.availablePages(words, unit1.copy(parts = setOf("B"))))
        assertEquals(listOf(10, 11, 13, 14), QuizSelection.availablePages(words, unit1.copy(parts = setOf(null, "B"))))
        assertEquals(listOf(21, 22), QuizSelection.availablePages(words, unit2.copy(parts = setOf("B"))))

        for (unit in QuizSelection.unitChoices(words)) {
            val options = if (unit == UnitChoice.All) listOf(emptySet()) else partSelections(QuizSelection.partOptions(words, unit))
            for (parts in options) {
                val filter = QuizSelection.withUnit(QuizFilter(), unit).copy(parts = parts)
                val expected = words.filter { matches(it, filter) }.mapNotNull { it.page }.distinct().sorted()
                assertEquals("$unit $parts", expected, QuizSelection.availablePages(words, filter))
            }
        }
    }

    @Test
    fun noReachableSelectionIsEmpty() = modes.forEach { words ->
        val filters = reachableFilters(words)
        // 3 units choices; unit 1 has 3 parts (7 part selections), unit 2 has 2 (3 selections).
        assertTrue(filters.size > 100)
        for (filter in filters) {
            val selected = QuizSelection.filter(words, filter)
            assertTrue("empty: $filter", selected.isNotEmpty())
            assertEquals("normalize keeps $filter", filter, QuizSelection.normalize(words, filter))
        }
    }

    // Rule 5: per entry, not per page.

    @Test
    fun everySelectedEntryMatchesUnitPartAndPage() = modes.forEach { words ->
        for (filter in reachableFilters(words)) {
            assertEquals(filter.toString(), words.filter { matches(it, filter) }, QuizSelection.filter(words, filter))
        }
    }

    @Test
    fun aSharedPageOnlyGivesTheEntriesOfTheSelectedParts() = modes.forEach { words ->
        val unit1 = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1))
        fun onPage13(vararg parts: String?) =
            QuizSelection.filter(words, QuizSelection.withPageFrom(unit1.copy(parts = parts.toSet()), 13)).map { it.part }

        assertEquals(listOf<String?>("A"), onPage13("A"))
        assertEquals(listOf<String?>("B"), onPage13("B"))
        assertEquals(listOf<String?>("A", "B"), onPage13("A", "B"))
        assertEquals(listOf<String?>("A", "B"), onPage13())
        // "Einstieg" ends on page 11, where Part A starts.
        val page11 = QuizSelection.withPageFrom(unit1.copy(parts = setOf(null)), 11)
        assertEquals(listOf<String?>(null), QuizSelection.filter(words, page11).map { it.part })
        // A range keeps the per-entry rule: Part B on 12–14 only has its own words of 13 and 14.
        val rangeB = QuizSelection.withPageFrom(unit1.copy(parts = setOf("B")), 13).copy(pageTo = 14)
        assertEquals(3, QuizSelection.filter(words, rangeB).size)
        assertEquals(pagesOf(words, 1, "B"), QuizSelection.filter(words, rangeB).mapNotNull { it.page }.toSet())
    }

    // Rule 6: Seiten never change Unit or Parts.

    @Test
    fun changingPagesKeepsUnitAndParts() = modes.forEach { words ->
        val start = QuizSelection.togglePart(QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1)), "A")
        for (page in listOf<Int?>(11, 12, 13, null)) {
            val changed = QuizSelection.withPageFrom(start, page)
            assertEquals(start.unit, changed.unit)
            assertEquals(start.parts, changed.parts)
        }
        val ranged = QuizSelection.withPageFrom(start, 11).copy(pageTo = 13)
        assertEquals(start.parts, ranged.parts)
        assertEquals(3, QuizSelection.filter(words, ranged).size)
    }

    // Order of changes on the screen.

    @Test
    fun unitThenPartsThenPagesNarrowsStepByStep() = modes.forEach { words ->
        val allParts = QuizSelection.partOptions(words, UnitChoice.Number(1))
        var filter = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1))
        assertEquals(8, QuizSelection.filter(words, filter).size)
        filter = QuizSelection.togglePart(filter, "A", allParts)
        filter = QuizSelection.togglePart(filter, "B", allParts)
        assertEquals(6, QuizSelection.filter(words, filter).size)
        filter = QuizSelection.withPageFrom(filter, 13)
        assertEquals(2, QuizSelection.filter(words, filter).size)
        filter = filter.copy(pageTo = 14)
        assertEquals(4, QuizSelection.filter(words, filter).size)

        // Upper level after a narrowed lower one: Parts reset the pages, Unit resets both.
        val partsChanged = QuizSelection.togglePart(filter, "B", allParts)
        assertEquals(QuizFilter(UnitChoice.Number(1), setOf("A")), partsChanged)
        assertEquals(3, QuizSelection.filter(words, partsChanged).size)
        val unitChanged = QuizSelection.withUnit(filter, UnitChoice.Number(2))
        assertEquals(QuizFilter(UnitChoice.Number(2)), unitChanged)
        assertEquals(5, QuizSelection.filter(words, unitChanged).size)
    }

    @Test
    fun selectingEveryPartIsTheSameAsAlleWithAllPages() = modes.forEach { words ->
        val allParts = QuizSelection.partOptions(words, UnitChoice.Number(2))
        var filter = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(2))
        filter = QuizSelection.togglePart(filter, "A", allParts)
        filter = QuizSelection.withPageFrom(filter, 21)
        filter = QuizSelection.togglePart(filter, "B", allParts)
        assertEquals(QuizFilter(UnitChoice.Number(2)), filter)
    }

    // The draft of the selection screen.

    @Test
    fun aDraftIsChangedOnlyIfItSelectsSomethingElse() {
        val snapshot = QuizSelection.togglePart(QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(1)), "A")
        assertFalse(QuizSelection.isChanged(vocabulary, snapshot, snapshot))
        // Ticked and unticked again: no change.
        val back = QuizSelection.togglePart(QuizSelection.togglePart(snapshot, "B"), "B")
        assertFalse(QuizSelection.isChanged(vocabulary, snapshot, back))
        assertTrue(QuizSelection.isChanged(vocabulary, snapshot, QuizSelection.withPageFrom(snapshot, 12)))
        assertTrue(QuizSelection.isChanged(vocabulary, snapshot, QuizSelection.withUnit(snapshot, UnitChoice.All)))
        // A stale stored filter equals its normalized form.
        assertFalse(QuizSelection.isChanged(vocabulary, QuizFilter(parts = setOf("A")), QuizFilter()))
    }
}
