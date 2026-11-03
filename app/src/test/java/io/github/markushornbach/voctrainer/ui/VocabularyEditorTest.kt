package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.VocTrainerJson
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.learning.exampleBookJson
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.WordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyEditorTest {
    /** The 150 entries of the example book, in file (insertion) order. */
    private val example: List<Vocabulary> = VocTrainerJson.parseImport(
        exampleBookJson()
    ).entries.mapIndexed { index, entry ->
        Vocabulary(
            id = "w$index", english = entry.english, germanMeanings = entry.german, unit = entry.unit,
            unitTitle = entry.unitTitle, part = entry.part, page = entry.page,
            wordType = VocTrainerJson.wordTypeOf(entry.type), extra = entry.extra
        )
    }

    private fun entry(
        id: String, english: String, german: String = "x", unit: Int? = 1, part: String? = null,
        page: Int? = null, level: Int = 0, type: WordType? = null, deleted: Boolean = false, title: String? = null
    ) = Vocabulary(
        id = id, english = english, germanMeanings = german.split(";"), unit = unit, unitTitle = title,
        part = part, page = page, wordType = type, leitnerLevel = level, isDeleted = deleted,
        highestLevel = level
    )

    private fun filter(all: List<Vocabulary>, filter: EditorFilter) = VocabularyEditor.filter(all, filter).map { it.english }

    @Test
    fun searchMatchesEnglishAndAnyGermanMeaningIgnoringCase() {
        assertEquals(listOf("present", "gift"), filter(example, EditorFilter(search = "GESCHENK")))
        assertEquals(listOf("ranger"), filter(example, EditorFilter(search = "Park")))
        val words = listOf(entry("a", "bag", "Tasche;Tüte"), entry("b", "bus", "Bus"))
        assertEquals(listOf("bag"), filter(words, EditorFilter(search = "  tüte ")))
        assertEquals(listOf("bag", "bus"), filter(words, EditorFilter(search = "  ")))
    }

    @Test
    fun levelFilterWithNothingSelectedShowsAllLevels() {
        val words = (0..5).map { entry("l$it", "word$it", level = it) }
        assertEquals(6, filter(words, EditorFilter()).size)
        assertEquals(listOf("word0", "word3"), filter(words, EditorFilter(levels = setOf(0, 3))))
    }

    @Test
    fun wordTypeFilterUsesTheEffectiveType() {
        val words = listOf(
            entry("a", "(to) run", "laufen"),                       // heuristic: verb
            entry("b", "dog", "Hund"),                              // heuristic: noun
            entry("c", "dog", "Hund", type = WordType.OTHER, unit = 2), // stored type wins
            entry("d", "See you!", "Bis bald!")                     // heuristic: phrase
        )
        assertEquals(listOf("(to) run"), filter(words, EditorFilter(types = setOf(WordType.VERB))))
        assertEquals("b", VocabularyEditor.filter(words, EditorFilter(types = setOf(WordType.NOUN))).single().id)
        assertEquals(listOf("See you!", "dog"), filter(words, EditorFilter(types = setOf(WordType.PHRASE, WordType.OTHER))))
        val example = VocabularyEditor.filter(example, EditorFilter(types = setOf(WordType.PHRASE)))
        assertEquals(18, example.size)
    }

    @Test
    fun ignoredModeShowsOnlyIgnoredEntriesAndOtherFiltersStillApply() {
        val words = listOf(
            entry("a", "bus", level = 2, deleted = true),
            entry("b", "car", level = 2),
            entry("c", "train", level = 4, deleted = true)
        )
        assertEquals(listOf("car"), filter(words, EditorFilter()))
        assertEquals(listOf("bus", "train"), filter(words, EditorFilter(showIgnored = true)))
        assertEquals(listOf("bus"), filter(words, EditorFilter(showIgnored = true, levels = setOf(2))))
    }

    @Test
    fun combinesWithUnitPartAndPagesIncludingPhrases() {
        val unit1PartA = EditorFilter(selection = QuizFilter(unit = UnitChoice.Number(1), parts = setOf("A")))
        assertEquals(16, VocabularyEditor.filter(example, unit1PartA).size)
        val page151 = unit1PartA.copy(selection = unit1PartA.selection.copy(pageFrom = 151))
        assertEquals(7, VocabularyEditor.filter(example, page151).size)
        val einstieg150to151 = EditorFilter(
            selection = QuizFilter(unit = UnitChoice.Number(1), parts = setOf(null), pageFrom = 150, pageTo = 151)
        )
        assertEquals(14, VocabularyEditor.filter(example, einstieg150to151).size)
        // The word-type filter "Redewendung".
        val phrases = EditorFilter(selection = QuizFilter(), types = setOf(WordType.PHRASE))
        assertEquals(18, VocabularyEditor.filter(example, phrases).size)
        assertEquals(150, VocabularyEditor.filter(example, EditorFilter()).size)
    }

    @Test
    fun sortsByUnitThenPageThenInsertionOrderWithMissingValuesLast() {
        val words = listOf(
            entry("1", "no unit", unit = null, page = 1),
            entry("2", "u2 p5", unit = 2, page = 5),
            entry("3", "u1 no page", unit = 1, page = null),
            entry("4", "u1 p9 first", unit = 1, page = 9),
            entry("5", "u1 p3", unit = 1, page = 3),
            entry("6", "u1 p9 second", unit = 1, page = 9)
        )
        assertEquals(
            listOf("u1 p3", "u1 p9 first", "u1 p9 second", "u1 no page", "u2 p5", "no unit"),
            filter(words, EditorFilter())
        )
        val sorted = VocabularyEditor.filter(example, EditorFilter())
        val keys = sorted.map { it.unit!! * 1000 + it.page!! }
        assertEquals(keys.sorted(), keys)
        // Equal unit and page keep the file order.
        sorted.groupBy { it.unit to it.page }.values.forEach { group ->
            val indexes = group.map { it.id.removePrefix("w").toInt() }
            assertEquals(indexes.sorted(), indexes)
        }
    }

    @Test
    fun normalizeKeepsSelectionsOfIgnoredEntries() {
        val words = listOf(entry("a", "bus", unit = 3, deleted = true), entry("b", "car", unit = 1))
        val chosen = EditorFilter(selection = QuizFilter(unit = UnitChoice.Number(3)), showIgnored = true)
        assertEquals(chosen.selection.unit, VocabularyEditor.normalize(words, chosen).selection.unit)
        val gone = chosen.copy(selection = QuizFilter(unit = UnitChoice.Number(7)))
        assertEquals(UnitChoice.All, VocabularyEditor.normalize(words, gone).selection.unit)
    }

    @Test
    fun summaryAndRowTexts() {
        assertEquals("Alle Vokabeln", VocabularyEditor.summary(EditorFilter(), emptyList()))
        val filter = EditorFilter(
            selection = QuizFilter(unit = UnitChoice.Number(1), parts = setOf("A", null), pageFrom = 151, pageTo = 152),
            search = " bus ", levels = setOf(3, 0), types = setOf(WordType.PHRASE, WordType.NOUN), showIgnored = true
        )
        assertEquals(
            "Unit 1 · Einstieg, A · S. 151–152 · „bus“ · Level 0, 3 · Nomen, Redewendung · Ignorierte",
            VocabularyEditor.summary(filter, listOf(null, "A", "B"))
        )
        assertEquals("Unit 1 · Einstieg · S. 150", VocabularyEditor.location(example.first()))
        assertEquals("Ohne Unit · A", VocabularyEditor.location(entry("x", "x", unit = null, part = "A")))
        assertEquals("1 Vokabel", VocabularyEditor.countLabel(1))
        assertEquals("23 Vokabeln", VocabularyEditor.countLabel(23))
        assertEquals("12 Wörter auf Level 2 gesetzt", VocabularyEditor.levelSetMessage(12, 2))
        assertEquals("1 Wort auf Level 0 gesetzt", VocabularyEditor.levelSetMessage(1, 0))
        assertEquals("Keine Änderung – alle Wörter sind schon auf Level 3", VocabularyEditor.levelSetMessage(0, 3))
    }

    // --- edit validation ---

    private val bag = entry("s", "bag", "Tasche;Tüte", unit = 1, part = "A", page = 151, level = 3, title = "Summer")
        .copy(coreWord = "bag")
    private val others = listOf(
        bag,
        entry("b", "bus", unit = 2, title = "School"),
        entry("g", "gone", unit = 1, deleted = true),
        entry("n", "new", unit = 3)
    )

    private fun validate(change: (EditInput) -> EditInput) =
        VocabularyEditor.validate(change(EditInput.of(bag)), bag, others)

    @Test
    fun unchangedInputIsValidAndKeepsEverything() {
        val result = validate { it }
        assertTrue(result.errors.isEmpty)
        assertEquals(bag, result.updated)
        assertEquals("Tasche; Tüte", EditInput.of(bag).german)
    }

    @Test
    fun loweringTheLevelKeepsTheHighestLevel() {
        val updated = validate { it.copy(level = 1) }.updated!!
        assertEquals(1, updated.leitnerLevel)
        assertEquals(3, updated.highestLevel)
    }

    @Test
    fun requiredFieldsHaveInlineErrors() {
        val result = validate { it.copy(english = " ", german = " ; ;", unit = "x", page = "0") }
        assertNull(result.updated)
        assertNotNull(result.errors.english)
        assertNotNull(result.errors.german)
        assertNotNull(result.errors.unit)
        assertNotNull(result.errors.page)
        assertNull(result.errors.duplicate)
        assertNotNull(validate { it.copy(unit = "0") }.errors.unit)
        assertNotNull(validate { it.copy(unit = "") }.errors.unit)
    }

    @Test
    fun germanIsSplitAtSemicolonsAndOptionalFieldsMayBeEmpty() {
        val updated = validate {
            it.copy(german = " Tüte ;; Tasche ; ", part = "  ", page = "", extra = " ", wordType = WordType.NOUN)
        }.updated!!
        assertEquals(listOf("Tüte", "Tasche"), updated.germanMeanings)
        assertNull(updated.part)
        assertNull(updated.page)
        assertNull(updated.extra)
        assertEquals(WordType.NOUN, updated.wordType)
        assertNull(validate { it.copy(wordType = null) }.updated!!.wordType)
    }

    @Test
    fun duplicateEnglishAndUnitIsRejectedIncludingIgnoredEntries() {
        assertEquals("Diese Vokabel gibt es in Unit 2 schon.", validate { it.copy(english = " BUS ", unit = "2") }.errors.duplicate)
        assertEquals("Diese Vokabel gibt es in Unit 1 schon.", validate { it.copy(english = "Gone") }.errors.duplicate)
        assertNull(validate { it.copy(english = "bus") }.errors.duplicate)
        assertNull(validate { it.copy(english = "BAG") }.errors.duplicate)
    }

    private fun validateAdmin(change: (EditInput) -> EditInput) =
        VocabularyEditor.validate(change(EditInput.of(bag)), bag, others, adminMode = true)

    @Test
    fun learningLevelMustBeBetweenZeroAndFive() {
        assertEquals(0, validateAdmin { it.copy(level = 0) }.updated!!.leitnerLevel)
        assertEquals(5, validateAdmin { it.copy(level = 5) }.updated!!.leitnerLevel)
        assertNotNull(validateAdmin { it.copy(level = 6) }.errors.level)
        assertNotNull(validateAdmin { it.copy(level = -1) }.errors.level)
        assertNotNull(validate { it.copy(level = -1) }.errors.level)
    }

    @Test
    fun withoutAdminModeTheLevelCanOnlyBeLowered() {
        // bag is stored with level 3.
        assertEquals(3, validate { it }.updated!!.leitnerLevel)
        assertEquals(1, validate { it.copy(level = 1) }.updated!!.leitnerLevel)
        assertEquals("Das Lernlevel kann nur gesenkt werden.", validate { it.copy(level = 4) }.errors.level)
        assertNull(validate { it.copy(level = 4) }.updated)
        assertEquals(3, VocabularyEditor.maxManualLevel(bag, adminMode = false))
        assertEquals(5, VocabularyEditor.maxManualLevel(bag, adminMode = true))
        assertEquals(4, validateAdmin { it.copy(level = 4) }.updated!!.leitnerLevel)
    }

    @Test
    fun unitTitleIsTakenFromTheNewUnitIfItHasOne() {
        assertEquals("School", validate { it.copy(unit = "2") }.updated!!.unitTitle)
        assertEquals("Summer", validate { it.copy(unit = "3") }.updated!!.unitTitle)
        assertEquals("Summer", validate { it.copy(english = "sack") }.updated!!.unitTitle)
    }

    // --- "Wort zum Tippen" ---

    private val careFor = entry("l", "care for sb.", level = 2).copy(coreWord = "for")

    private fun validateCareFor(input: EditInput) = VocabularyEditor.validate(input, careFor, others + careFor)

    @Test
    fun storedCoreWordIsPreselectedWhileItIsStillAWordOfTheText() {
        assertEquals(CoreWordChoice("for", tapped = true), EditInput.of(careFor).coreWord)
        assertEquals("for", validateCareFor(EditInput.of(careFor)).updated!!.coreWord)
        // Changing another part of the text keeps the word.
        val changed = EditInput.of(careFor).withEnglish("(to) care for sb./sth.")
        assertEquals("for", validateCareFor(changed).updated!!.coreWord)
    }

    @Test
    fun aVanishedWordIsClearedAndSavingNeedsANewOne() {
        val input = EditInput.of(careFor).withEnglish("care about sb.")
        assertEquals(CoreWordChoice(), input.coreWord)
        val result = validateCareFor(input)
        assertNull(result.updated)
        assertEquals("Bitte tippe ein Wort an.", result.errors.coreWord)

        val chosen = validateCareFor(input.copy(coreWord = CoreWordChoice.tap("about"))).updated!!
        assertEquals("about", chosen.coreWord)
        assertEquals("care about sb.", chosen.english)
        assertEquals(2, chosen.leitnerLevel)
    }

    @Test
    fun aSingleWordIsChosenAutomatically() {
        val input = EditInput.of(careFor).withEnglish("(to) walk")
        assertEquals(CoreWordChoice("walk"), input.coreWord)
        assertEquals("walk", validateCareFor(input).updated!!.coreWord)
        // A stored core word that is not a word of the text (old data) is replaced by the only word.
        val outdated = bag.copy(coreWord = "sack")
        assertEquals("bag", VocabularyEditor.validate(EditInput.of(outdated), outdated, others).updated!!.coreWord)
    }

    @Test
    fun anAutomaticChoiceIsClearedWhenMoreWordsAreTyped() {
        // Typing "care for" letter by letter: "care" is chosen automatically, then the learner must tap.
        val typing = EditInput().withEnglish("care")
        assertEquals(CoreWordChoice("care"), typing.coreWord)
        assertEquals(CoreWordChoice(), typing.withEnglish("care for").coreWord)
        // A tapped word stays while it is a word of the text.
        val tapped = typing.withEnglish("care for").copy(coreWord = CoreWordChoice.tap("for"))
        assertEquals(CoreWordChoice("for", tapped = true), tapped.withEnglish("care for sb.").coreWord)
        assertEquals(CoreWordChoice("For", tapped = true), tapped.withEnglish("Care For sb.").coreWord)
    }

    @Test
    fun englishWithoutAnyWordHasAnErrorAtTheEnglishField() {
        val result = validate { it.withEnglish("…") }
        assertNull(result.updated)
        assertEquals("Bitte gib mindestens ein englisches Wort ein.", result.errors.english)
        assertNull(result.errors.coreWord)
    }

    @Test
    fun savingClearsAnOldMultipleChoiceOnlyMark() {
        val marked = bag.copy(choiceOnly = true)
        assertEquals(false, VocabularyEditor.validate(EditInput.of(marked), marked, others).updated!!.choiceOnly)
    }

    @Test
    fun restoringNeedsAUsableCoreWord() {
        assertTrue(VocabularyEditor.needsCoreWordToRestore(entry("a", "care for sb.", deleted = true)))
        assertTrue(VocabularyEditor.needsCoreWordToRestore(entry("a", "care for sb.", deleted = true).copy(coreWord = "about")))
        assertEquals(false, VocabularyEditor.needsCoreWordToRestore(entry("a", "bus", deleted = true).copy(coreWord = "bus")))
    }

    @Test
    fun theEditorStartsWithTheQuizSelectionAndEmptyOtherFilters() {
        val quiz = QuizFilter(unit = UnitChoice.Number(2), parts = setOf("A", null), pageFrom = 20, pageTo = 22)
        assertEquals(EditorFilter(selection = quiz), VocabularyEditor.startFilter(quiz))
        assertEquals(EditorFilter(), VocabularyEditor.startFilter(null))
    }
}
