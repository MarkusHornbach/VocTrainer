package io.github.markushornbach.voctrainer.learning

import io.github.markushornbach.voctrainer.data.VocTrainerFile
import io.github.markushornbach.voctrainer.data.VocTrainerJson
import java.io.File

/** The example book, the app's asset `beispielbuch.json` (read from the source tree, no copy). */
const val EXAMPLE_BOOK = "beispielbuch"

/** The example book without pages and word types, like an old import file (see [exampleFile]). */
const val EXAMPLE_BOOK_OLD = "beispielbuch-without-pages"

/** The text of the example book; the unit tests run in the module directory (Gradle and Android Studio). */
fun exampleBookJson(): String =
    listOf("src/main/assets/beispielbuch.json", "app/src/main/assets/beispielbuch.json")
        .map(::File).first { it.exists() }.readText()

/** The parsed example book; [EXAMPLE_BOOK_OLD] is built from it. */
fun exampleFile(file: String = EXAMPLE_BOOK): VocTrainerFile {
    val parsed = VocTrainerJson.parseImport(exampleBookJson())
    if (file == EXAMPLE_BOOK) return parsed
    require(file == EXAMPLE_BOOK_OLD) { file }
    return parsed.copy(
        entries = parsed.entries.map { it.copy(page = null, type = null) },
        irregularVerbs = parsed.irregularVerbs.map { it.copy(page = null) }
    )
}

fun word(
    id: String,
    english: String,
    vararg german: String,
    unit: Int? = 1,
    part: String? = "A",
    level: Int = 0,
    page: Int? = null,
    type: WordType? = null
) = QuizWord(id, english, german.toList(), unit, part, level, page, type)

/** Without core words, so these test words are asked with multiple choice on every level. */
fun exampleWords(file: String = EXAMPLE_BOOK_OLD): List<QuizWord> {
    return exampleFile(file).entries.mapIndexed { index, entry ->
        QuizWord(
            "w$index", entry.english, entry.german, entry.unit, entry.part, 0,
            entry.page, VocTrainerJson.wordTypeOf(entry.type)
        )
    }
}

/** The irregular verbs of the example book. */
fun irregularFixtureVerbs(): List<QuizWord> =
    exampleFile().irregularVerbs.mapIndexed { index, verb ->
        QuizWord(
            id = "v$index",
            english = verb.infinitive,
            germanMeanings = verb.german,
            unit = verb.unit,
            part = verb.part,
            leitnerLevel = 0,
            page = verb.page,
            storedType = WordType.VERB,
            verbForms = VerbForms(verb.simplePast, verb.pastParticiple)
        )
    }

/** The vocabulary entries of type verb of the example book. */
fun vocabularyVerbFixture(): List<QuizWord> =
    exampleFile().entries.filter { VocTrainerJson.wordTypeOf(it.type) == WordType.VERB }.mapIndexed { index, entry ->
        QuizWord(
            id = "w$index",
            english = entry.english,
            germanMeanings = entry.german,
            unit = entry.unit,
            part = entry.part,
            leitnerLevel = 0,
            page = entry.page,
            storedType = WordType.VERB
        )
    }
