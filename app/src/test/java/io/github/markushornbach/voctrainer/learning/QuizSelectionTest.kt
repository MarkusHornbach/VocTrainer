package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizSelectionTest {
    // Insertion order: part B appears before part A in the textbook.
    private val words = listOf(
        word("1", "a", "A", unit = 2, part = "B", page = 10),
        word("2", "b", "B", unit = 2, part = null, page = 9),
        word("3", "c", "C", unit = 2, part = "A", page = 11),
        word("4", "d", "D", unit = 1, part = "Pet diary", page = 5),
        word("5", "e", "E", unit = 2, part = "B", page = 11),
        word("6", "Nice to meet you.", "Freut mich.", unit = 2, part = "B", page = 11),
        word("7", "g", "G", unit = 2, part = "A", page = null)
    )

    private fun ids(filter: QuizFilter) = QuizSelection.filter(words, filter).map { it.id }

    @Test
    fun unitsAreAscendingWithOhneUnitOnlyWhenNeeded() {
        assertEquals(
            listOf(UnitChoice.All, UnitChoice.Number(1), UnitChoice.Number(2)),
            QuizSelection.unitChoices(words)
        )
        assertEquals(
            listOf(UnitChoice.All, UnitChoice.Number(1), UnitChoice.Number(2), UnitChoice.WithoutUnit),
            QuizSelection.unitChoices(words + word("x", "f", "F", unit = null, part = null))
        )
    }

    @Test
    fun partsFollowTextbookOrderWithEinstiegFirst() {
        assertEquals(listOf(null, "B", "A"), QuizSelection.partOptions(words, UnitChoice.Number(2)))
        assertEquals(emptyList<String?>(), QuizSelection.partOptions(words, UnitChoice.All))
    }

    @Test
    fun severalPartsCanBeCombined() {
        val unit2 = QuizFilter(unit = UnitChoice.Number(2))
        assertEquals(listOf("1", "2", "3", "5", "6", "7"), ids(unit2))
        assertEquals(listOf("1", "5", "6"), ids(unit2.copy(parts = setOf("B"))))
        assertEquals(listOf("2", "3", "7"), ids(unit2.copy(parts = setOf(null, "A"))))
    }

    @Test
    fun pageFilterSinglePageAndRange() {
        val all = QuizFilter()
        assertEquals(listOf("3", "5", "6"), ids(all.copy(pageFrom = 11)))
        assertEquals(listOf("1", "2", "3", "5", "6"), ids(all.copy(pageFrom = 9, pageTo = 11)))
        assertEquals(
            listOf("3"),
            ids(all.copy(unit = UnitChoice.Number(2), parts = setOf("A"), pageFrom = 10, pageTo = 11))
        )
    }

    @Test
    fun wordsWithoutPageAreOnlyExcludedWhenAPageFilterIsSet() {
        val unit2A = QuizFilter(unit = UnitChoice.Number(2), parts = setOf("A"))
        assertEquals(listOf("3", "7"), ids(unit2A))
        assertEquals(listOf("3"), ids(unit2A.copy(pageFrom = 9, pageTo = 11)))
    }

    @Test
    fun phrasesArePartOfTheSelection() {
        val unit2B = QuizFilter(unit = UnitChoice.Number(2), parts = setOf("B"))
        assertEquals(WordType.PHRASE, words.single { it.id == "6" }.effectiveType)
        assertEquals(listOf("1", "5", "6"), ids(unit2B))
    }

    @Test
    fun availablePagesDependOnUnitAndParts() {
        assertEquals(listOf(5, 9, 10, 11), QuizSelection.availablePages(words, QuizFilter()))
        assertEquals(listOf(9, 10, 11), QuizSelection.availablePages(words, QuizFilter(unit = UnitChoice.Number(2))))
        assertEquals(listOf(10, 11), QuizSelection.availablePages(words, QuizFilter(UnitChoice.Number(2), setOf("B"))))
    }

    @Test
    fun normalizeResetsSelectionsThatNoLongerExist() {
        val stale = QuizFilter(unit = UnitChoice.Number(1), parts = setOf("B"), pageFrom = 10, pageTo = 11)
        val normalized = QuizSelection.normalize(words, stale)
        assertEquals(emptySet<String?>(), normalized.parts)
        assertNull(normalized.pageFrom)
        assertNull(normalized.pageTo)

        val toBeforeFrom = QuizSelection.normalize(words, QuizFilter(pageFrom = 10, pageTo = 9))
        assertEquals(10, toBeforeFrom.pageFrom)
        assertNull(toBeforeFrom.pageTo)

        assertEquals(UnitChoice.All, QuizSelection.normalize(words, QuizFilter(unit = UnitChoice.Number(7))).unit)
    }

    @Test
    fun pageRangeSurvivesPartChangesWhileItStillCoversWords() {
        // "Einstieg" of unit 2 only has page 9, but the range 9–11 still applies to it.
        val range = QuizFilter(unit = UnitChoice.Number(2), parts = setOf(null), pageFrom = 9, pageTo = 11)
        assertEquals(range, QuizSelection.normalize(words, range))
        // Part A only has page 11: the range 9–11 still covers it.
        val partA = range.copy(parts = setOf("A"))
        assertEquals(partA, QuizSelection.normalize(words, partA))
        // Only page 9 selected, but Part A has no word on page 9 → reset.
        val normalized = QuizSelection.normalize(words, partA.copy(pageTo = null))
        assertNull(normalized.pageFrom)
    }

    @Test
    fun textbookExampleKeepsTheRangeWhenPartsAreTickedOneByOne() {
        val example = exampleWords(EXAMPLE_BOOK)
        var filter = QuizFilter(unit = UnitChoice.Number(1), pageFrom = 150, pageTo = 152)
        filter = QuizSelection.normalize(example, filter.copy(parts = setOf(null)))
        filter = QuizSelection.normalize(example, filter.copy(parts = filter.parts + "A"))
        assertEquals(150, filter.pageFrom)
        assertEquals(152, filter.pageTo)
        assertEquals(30, QuizSelection.filter(example, filter).size)
    }

    @Test
    fun exampleFileUnitsAndParts() {
        assertEquals(
            listOf(null, "A", "B", "Pet diary"),
            QuizSelection.partOptions(exampleWords(), UnitChoice.Number(1))
        )
    }

    @Test
    fun textbookExamplePages150ToMiddleOf152() {
        val example = exampleWords(EXAMPLE_BOOK)
        val unit1 = QuizFilter(unit = UnitChoice.Number(1), pageFrom = 150, pageTo = 152)

        // "Einstieg" + Part A is exactly "page 150 to the middle of 152".
        assertEquals(30, QuizSelection.filter(example, unit1.copy(parts = setOf(null, "A"))).size)
        // All parts on pages 150–152 also contain the first 3 words of Part B on page 152.
        assertEquals(33, QuizSelection.filter(example, unit1).size)
        // Part A alone misses the "Einstieg" words.
        assertEquals(16, QuizSelection.filter(example, unit1.copy(parts = setOf("A"))).size)
        assertEquals(listOf(150, 151, 152), QuizSelection.availablePages(example, unit1.copy(parts = setOf(null, "A"))))
    }

    // "Alle" chip rules: "Alle" is on exactly when nothing is narrowed down (single source of truth).

    private val allParts = listOf<String?>(null, "B", "A")

    @Test
    fun defaultFilterIsAlleEverywhere() {
        val f = QuizFilter()
        assertEquals(UnitChoice.All, f.unit)
        assertEquals(emptySet<String?>(), f.parts)
        assertNull(f.pageFrom)
    }

    @Test
    fun partsToggleInCombinationAndTapAlleClearsThem() {
        var f = QuizFilter(UnitChoice.Number(2))
        f = QuizSelection.togglePart(f, "B", allParts)
        f = QuizSelection.togglePart(f, "A", allParts)
        assertEquals(setOf<String?>("B", "A"), f.parts)
        f = f.copy(parts = emptySet()) // tap "Alle" while off
        assertEquals(emptySet<String?>(), f.parts)
    }

    @Test
    fun deselectingTheLastPartTurnsAlleOn() {
        var f = QuizSelection.togglePart(QuizFilter(UnitChoice.Number(2)), "B", allParts)
        f = QuizSelection.togglePart(f, "B", allParts)
        assertEquals(emptySet<String?>(), f.parts)
    }

    @Test
    fun selectingEveryPartCollapsesToAlle() {
        var f = QuizFilter(UnitChoice.Number(2))
        listOf<String?>(null, "B", "A").forEach { f = QuizSelection.togglePart(f, it, allParts) }
        assertEquals(emptySet<String?>(), f.parts)
        // Without the option list (callers that do not know it) nothing collapses.
        assertEquals(setOf<String?>("B"), QuizSelection.togglePart(QuizFilter(), "B").parts)
    }

    @Test
    fun unitIsSingleSelectAndChangingItResetsParts() {
        var f = QuizSelection.withUnit(QuizFilter(), UnitChoice.Number(2))
        f = QuizSelection.togglePart(f, "B", allParts)
        f = QuizSelection.withUnit(f, UnitChoice.Number(1))
        assertEquals(UnitChoice.Number(1), f.unit)
        assertEquals(emptySet<String?>(), f.parts)
        assertEquals(UnitChoice.All, QuizSelection.withUnit(f, UnitChoice.All).unit)
    }

    @Test
    fun pageRangeAndAlleAreInSync() {
        val ranged = QuizSelection.withPageFrom(QuizFilter(), 9).copy(pageTo = 11)
        assertEquals(9, ranged.pageFrom)
        // "Alle" chip and the "Alle Seiten" dropdown entry both call withPageFrom(null).
        val all = QuizSelection.withPageFrom(ranged, null)
        assertNull(all.pageFrom)
        assertNull(all.pageTo)
    }
}
