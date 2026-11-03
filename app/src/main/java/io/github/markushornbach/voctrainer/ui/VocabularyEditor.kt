package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.CoreWords
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.VocabularyImporter
import io.github.markushornbach.voctrainer.data.toQuizWord
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.WordType

/** Filters of the "Vokabeln editieren" list; combined with AND. */
data class EditorFilter(
    /** Unit/Part/pages as on the quiz start screen; phrases are always included here. */
    val selection: QuizFilter = QuizFilter(),
    val search: String = "",
    /** Empty = all levels. */
    val levels: Set<Int> = emptySet(),
    /** Effective word types; empty = all types. */
    val types: Set<WordType> = emptySet(),
    /** true = show only ignored (soft-deleted) entries instead of the active ones. */
    val showIgnored: Boolean = false
)

/** Word types in the order of the filter chips and the dropdown. */
val EDITOR_WORD_TYPES = listOf(WordType.NOUN, WordType.VERB, WordType.OTHER, WordType.PHRASE)

fun wordTypeLabel(type: WordType): String = when (type) {
    WordType.NOUN -> "Nomen"
    WordType.VERB -> "Verb"
    WordType.OTHER -> "Sonstige"
    WordType.PHRASE -> "Redewendung"
}

val Vocabulary.effectiveType: WordType
    get() = WordType.effective(wordType, english, germanMeanings.first())

object VocabularyEditor {
    /**
     * Unit, Part and page choices come from all entries including ignored ones, so switching
     * "Ignorierte anzeigen" keeps the selection. [all] must be in insertion (textbook) order.
     */
    fun normalize(all: List<Vocabulary>, filter: EditorFilter): EditorFilter =
        filter.copy(selection = QuizSelection.normalize(all.map { it.toQuizWord() }, filter.selection))

    /** Matching entries in textbook order. [all] must be in insertion order and include ignored entries. */
    fun filter(all: List<Vocabulary>, filter: EditorFilter): List<Vocabulary> {
        val candidates = all.filter { it.isDeleted == filter.showIgnored }
        val inSelection = QuizSelection.filter(candidates.map { it.toQuizWord() }, filter.selection)
            .mapTo(HashSet()) { it.id }
        val search = filter.search.trim()
        return textbookOrder(
            candidates.filter { entry ->
                entry.id in inSelection &&
                    (search.isEmpty() || entry.english.contains(search, ignoreCase = true) ||
                        entry.germanMeanings.any { it.contains(search, ignoreCase = true) }) &&
                    (filter.levels.isEmpty() || entry.leitnerLevel in filter.levels) &&
                    (filter.types.isEmpty() || entry.effectiveType in filter.types)
            }
        )
    }

    /** Unit (none last) → page (none last); the stable sort keeps the insertion order as tie-breaker. */
    fun textbookOrder(entries: List<Vocabulary>): List<Vocabulary> =
        entries.sortedWith(compareBy({ it.unit == null }, { it.unit }, { it.page == null }, { it.page }))

    /** One line for the collapsed filter area, e.g. "Unit 1 · A · S. 202 · „bus“ · Level 0, 1". */
    fun summary(filter: EditorFilter, partOptions: List<String?>): String = buildList {
        val selection = filter.selection
        if (selection.unit != UnitChoice.All) {
            add(unitLabel(selection.unit))
            if (selection.parts.isNotEmpty()) {
                add(partOptions.filter { it in selection.parts }.joinToString(", ") { partLabel(it) })
            }
        }
        selection.pageFrom?.let { from ->
            val to = selection.pageTo
            add(if (to == null || to == from) "S. $from" else "S. $from–$to")
        }
        filter.search.trim().takeIf { it.isNotEmpty() }?.let { add("„$it“") }
        if (filter.levels.isNotEmpty()) add("Level " + filter.levels.sorted().joinToString(", "))
        if (filter.types.isNotEmpty()) {
            add(EDITOR_WORD_TYPES.filter { it in filter.types }.joinToString(", ") { wordTypeLabel(it) })
        }
        if (filter.showIgnored) add("Ignorierte")
    }.joinToString(" · ").ifEmpty { "Alle Vokabeln" }

    fun countLabel(count: Int): String = if (count == 1) "1 Vokabel" else "$count Vokabeln"

    /** Snackbar after setting the level of the selection. */
    /**
     * Filter when "Vokabeln editieren" is opened: Unit/Part/pages of the quiz's vocabulary
     * selection ([quizSelection], null = none saved yet), everything else empty.
     */
    fun startFilter(quizSelection: QuizFilter?): EditorFilter = EditorFilter(selection = quizSelection ?: QuizFilter())

    fun levelSetMessage(changed: Int, level: Int): String = when (changed) {
        0 -> "Keine Änderung – alle Wörter sind schon auf Level $level"
        1 -> "1 Wort auf Level $level gesetzt"
        else -> "$changed Wörter auf Level $level gesetzt"
    }

    /** "Unit 1 · A · S. 202"; part null is "Einstieg". */
    fun location(entry: Vocabulary): String = listOfNotNull(
        entry.unit?.let { "Unit $it" } ?: "Ohne Unit",
        partLabel(entry.part),
        entry.page?.let { "S. $it" }
    ).joinToString(" · ")

    /** Highest level that may be set by hand: without admin mode only the stored level or lower. */
    fun maxManualLevel(original: Vocabulary, adminMode: Boolean): Int =
        if (adminMode) Leitner.MAX_LEVEL else original.leitnerLevel.coerceIn(0, Leitner.MAX_LEVEL)

    fun validate(
        input: EditInput,
        original: Vocabulary,
        all: List<Vocabulary>,
        adminMode: Boolean = false
    ): EditValidation {
        val english = input.english.trim()
        val meanings = input.german.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val unit = input.unit.trim().toIntOrNull()?.takeIf { it >= 1 }
        val pageText = input.page.trim()
        val page = pageText.toIntOrNull()?.takeIf { it >= 1 }
        val duplicateUnit = if (english.isNotEmpty() && unit != null) {
            val key = VocabularyImporter.vocabularyKey(english, unit)
            val changed = key != VocabularyImporter.vocabularyKey(original.english, original.unit)
            unit.takeIf {
                changed && all.any { it.id != original.id && VocabularyImporter.vocabularyKey(it.english, it.unit) == key }
            }
        } else {
            null
        }
        val coreWord = input.coreWord.chosenIn(english)
        val errors = EditErrors(
            english = if (english.isEmpty()) "Bitte das englische Wort eingeben." else CoreWordTexts.englishError(english),
            coreWord = CoreWordTexts.choiceError(english, input.coreWord),
            german = "Bitte mindestens eine deutsche Bedeutung eingeben.".takeIf { meanings.isEmpty() },
            unit = "Bitte eine gültige Unit-Nummer eingeben.".takeIf { unit == null },
            page = "Bitte eine gültige Seitenzahl eingeben oder das Feld leer lassen."
                .takeIf { pageText.isNotEmpty() && page == null },
            level = when {
                input.level !in 0..Leitner.MAX_LEVEL -> "Das Lernlevel muss zwischen 0 und ${Leitner.MAX_LEVEL} liegen."
                input.level > maxManualLevel(original, adminMode) -> "Das Lernlevel kann nur gesenkt werden."
                else -> null
            },
            duplicate = duplicateUnit?.let { "Diese Vokabel gibt es in Unit $it schon." }
        )
        if (!errors.isEmpty || unit == null || coreWord == null) return EditValidation(errors, null)
        val unitTitle = if (unit == original.unit) {
            original.unitTitle
        } else {
            all.firstOrNull { it.unit == unit && it.unitTitle != null }?.unitTitle ?: original.unitTitle
        }
        return EditValidation(
            errors,
            original.withLevel(input.level).copy(
                english = english,
                germanMeanings = meanings,
                unit = unit,
                unitTitle = unitTitle,
                part = input.part.trim().ifEmpty { null },
                page = page,
                wordType = input.wordType,
                extra = input.extra.trim().ifEmpty { null },
                coreWord = coreWord,
                choiceOnly = false
            )
        )
    }

    /**
     * An ignored entry that cannot be restored as it is: it has no core word that is a word of its
     * English text, so "Wiederherstellen" opens the edit page to choose one.
     */
    fun needsCoreWordToRestore(entry: Vocabulary): Boolean = CoreWords.candidateFor(entry.coreWord, entry.english) == null
}

/** Raw form values of the edit page. */
data class EditInput(
    val english: String = "",
    val german: String = "",
    val unit: String = "",
    val part: String = "",
    val page: String = "",
    /** null = "Automatisch" (heuristic). */
    val wordType: WordType? = null,
    val extra: String = "",
    val level: Int = 0,
    /** "Wort zum Tippen". */
    val coreWord: CoreWordChoice = CoreWordChoice()
) {
    /** The English text changed: the chosen word stays only while it is still a word of the text. */
    fun withEnglish(text: String): EditInput = copy(english = text, coreWord = coreWord.afterEnglishChange(text))

    companion object {
        fun of(entry: Vocabulary) = EditInput(
            coreWord = CoreWordChoice.stored(entry.coreWord, entry.english),
            english = entry.english,
            german = entry.germanMeanings.joinToString("; "),
            unit = entry.unit?.toString().orEmpty(),
            part = entry.part.orEmpty(),
            page = entry.page?.toString().orEmpty(),
            wordType = entry.wordType,
            extra = entry.extra.orEmpty(),
            level = entry.leitnerLevel
        )
    }
}

data class EditErrors(
    val english: String? = null,
    val german: String? = null,
    val unit: String? = null,
    val page: String? = null,
    val level: String? = null,
    val duplicate: String? = null,
    val coreWord: String? = null
) {
    val isEmpty: Boolean get() = listOf(english, german, unit, page, level, duplicate, coreWord).all { it == null }
}

/** [updated] is the changed entry (without a new updatedAt), or null if [errors] is not empty. */
data class EditValidation(val errors: EditErrors, val updated: Vocabulary?)
