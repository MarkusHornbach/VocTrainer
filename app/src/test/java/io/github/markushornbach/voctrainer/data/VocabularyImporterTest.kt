package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.EXAMPLE_BOOK_OLD
import io.github.markushornbach.voctrainer.learning.EXAMPLE_BOOK
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.learning.exampleFile
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyImporterTest {
    private val now = 1_000_000L

    private fun file(
        entries: List<VocabularyJson> = emptyList(),
        verbs: List<IrregularVerbJson> = emptyList()
    ) = VocTrainerFile(format = VocTrainerJson.IMPORT_FORMAT, version = 1, entries = entries, irregularVerbs = verbs)

    private fun entry(english: String, unit: Int, german: String = "x") =
        VocabularyJson(english = english, german = listOf(german), unit = unit)

    private fun verb(infinitive: String, pastParticiple: String? = null, part: String? = null) =
        IrregularVerbJson(
            infinitive = infinitive, simplePast = "past", pastParticiple = pastParticiple,
            german = listOf("x"), unit = 1, part = part
        )

    @Test
    fun skipsVocabularyWithSameNormalizedEnglishAndUnit() {
        val existing = Vocabulary(
            id = "keep", english = "Get  off", germanMeanings = listOf("aussteigen"), unit = 1,
            leitnerLevel = 4, updatedAt = 5L
        )

        val plan = VocabularyImporter.plan(
            file(listOf(entry("  get off ", 1, "anders"), entry("get off", 2), entry("bus", 1))),
            listOf(existing), emptyList(), now
        )

        assertEquals(1, plan.skippedVocabulary)
        assertEquals(listOf("get off" to 2, "bus" to 1), plan.newVocabulary.map { it.english to it.unit })
        assertTrue(plan.newVocabulary.none { it.id == existing.id })
    }

    @Test
    fun deletedVocabularyCountsAsPresent() {
        val deleted = Vocabulary(english = "bus", germanMeanings = listOf("Bus"), unit = 1, isDeleted = true)

        val plan = VocabularyImporter.plan(file(listOf(entry("bus", 1))), listOf(deleted), emptyList(), now)

        assertEquals(0, plan.newVocabulary.size)
        assertEquals(1, plan.skippedVocabulary)
        assertEquals(deleted.id, plan.completedVocabulary.single().id)
    }

    @Test
    fun duplicatesInsideTheFileAreImportedOnce() {
        val plan = VocabularyImporter.plan(file(listOf(entry("bus", 1), entry("Bus", 1))), emptyList(), emptyList(), now)

        assertEquals(1, plan.newVocabulary.size)
        assertEquals(1, plan.skippedVocabulary)
    }

    @Test
    fun fillsMissingVerbFormsWithoutOverwritingOrResettingProgress() {
        val catch = IrregularVerb(
            id = "c", infinitive = "(to) catch", simplePast = "caught", pastParticiple = null,
            germanMeanings = listOf("fangen"), unit = 1, part = null, leitnerLevel = 3, updatedAt = 1L
        )
        val go = IrregularVerb(
            id = "g", infinitive = "(to) go", simplePast = "went", pastParticiple = "gone",
            germanMeanings = listOf("gehen"), unit = 1, part = "A", leitnerLevel = 2, updatedAt = 1L
        )

        val plan = VocabularyImporter.plan(
            file(verbs = listOf(verb("catch", pastParticiple = "caught", part = "B"), verb("(to) GO", "WRONG", "Z"))),
            emptyList(), listOf(catch, go), now
        )

        assertEquals(0, plan.newVerbs.size)
        val updated = plan.updatedVerbs.single()
        assertEquals("c", updated.id)
        assertEquals("caught", updated.pastParticiple)
        assertEquals("B", updated.part)
        assertEquals("caught", updated.simplePast)
        assertEquals(listOf("fangen"), updated.germanMeanings)
        assertEquals(3, updated.leitnerLevel)
        assertEquals(now, updated.updatedAt)
    }

    @Test
    fun addsUnknownVerbs() {
        val plan = VocabularyImporter.plan(file(verbs = listOf(verb("(to) swim"))), emptyList(), emptyList(), now)

        val added = plan.newVerbs.single()
        assertEquals("(to) swim", added.infinitive)
        assertEquals(0, added.leitnerLevel)
        assertEquals(0, plan.updatedVerbs.size)
    }

    @Test
    fun importingTheExampleFileTwiceImportsNothingTheSecondTime() {
        val example = exampleFile(EXAMPLE_BOOK_OLD)

        val first = VocabularyImporter.plan(example, emptyList(), emptyList(), now)
        assertEquals(VocabularyImportResult(150, 0, 36, 0), first.result)

        val second = VocabularyImporter.plan(example, first.newVocabulary, first.newVerbs, now + 1)
        assertEquals(VocabularyImportResult(0, 150, 0, 0), second.result)
    }

    @Test
    fun reImportOfTheNewFileCompletesPagesAndTypesWithoutTouchingProgress() {
        val first = VocabularyImporter.plan(exampleFile(EXAMPLE_BOOK_OLD), emptyList(), emptyList(), now)
        val existing = first.newVocabulary.mapIndexed { index, it -> it.copy(leitnerLevel = index % 6) }
        assertTrue(existing.all { it.coreWord != null })

        val second = VocabularyImporter.plan(exampleFile(EXAMPLE_BOOK), existing, first.newVerbs, now + 1)

        assertEquals(VocabularyImportResult(0, 150, 0, 36, vocabularyCompleted = 150, vocabularyUpdated = 150), second.result)
        val completed = second.completedVocabulary.associateBy { it.id }
        existing.forEach { before ->
            val after = completed.getValue(before.id)
            assertEquals(before.leitnerLevel, after.leitnerLevel)
            assertEquals(before.copy(page = after.page, wordType = after.wordType, updatedAt = now + 1), after)
            assertTrue(after.page != null && after.wordType != null)
        }
        assertEquals(18, second.completedVocabulary.count { it.wordType == WordType.PHRASE })
        assertTrue(second.updatedVerbs.all { it.page != null })

        val third = VocabularyImporter.plan(
            exampleFile(EXAMPLE_BOOK), second.completedVocabulary, second.updatedVerbs, now + 2
        )
        assertEquals(VocabularyImportResult(0, 150, 0, 0, vocabularyCompleted = 0), third.result)
    }

    /** [VocabularyRepository.ensureExampleBook] applies the example book at every start: the second run changes nothing. */
    @Test
    fun applyingTheExampleBookAgainChangesNothing() {
        val first = VocabularyImporter.plan(exampleFile(), emptyList(), emptyList(), now, exampleBook = true)
        assertEquals(VocabularyImportResult(150, 0, 36, 0), first.result)
        assertTrue(first.newVocabulary.none { it.isDeleted || it.coreWord == null })
        val stored = first.newVocabulary.mapIndexed { index, it -> it.copy(leitnerLevel = index % 6, isDeleted = index == 3) }
        val verbs = first.newVerbs.map { it.copy(leitnerLevel = 2) }

        val again = VocabularyImporter.plan(exampleFile(), stored, verbs, now + 1, exampleBook = true)

        assertEquals(VocabularyImportResult(0, 150, 0, 0), again.result)
        assertTrue(again.completedVocabulary.isEmpty() && again.updatedVerbs.isEmpty())
    }

    @Test
    fun theExampleBookKeepsACoreWordTheLearnerChanged() {
        val first = VocabularyImporter.plan(exampleFile(), emptyList(), emptyList(), now, exampleBook = true)
        val shelter = first.newVocabulary.first { it.english == "animal shelter" }
        assertEquals("shelter", shelter.coreWord)
        val changed = first.newVocabulary.map { if (it.id == shelter.id) it.copy(coreWord = "animal") else it }

        val again = VocabularyImporter.plan(exampleFile(), changed, first.newVerbs, now + 1, exampleBook = true)
        assertTrue(again.completedVocabulary.isEmpty())
        // A normal import takes over the core word of the file.
        val normal = VocabularyImporter.plan(exampleFile(), changed, first.newVerbs, now + 1)
        assertEquals("shelter", normal.completedVocabulary.single().coreWord)
    }

    @Test
    fun theExampleBookNeverOverwritesPageOrTypeThatIsAlreadySet() {
        val bothSet = Vocabulary(id = "a", english = "bus", germanMeanings = listOf("Bus"), unit = 1, page = 999, wordType = WordType.VERB)
        val onlyPage = Vocabulary(id = "b", english = "car", germanMeanings = listOf("Auto"), unit = 1, page = 998)
        val deleted = Vocabulary(id = "c", english = "bike", germanMeanings = listOf("Rad"), unit = 1, isDeleted = true)
        val file = VocTrainerFile(
            format = VocTrainerJson.IMPORT_FORMAT, version = 1,
            entries = listOf("bus", "car", "bike").map {
                VocabularyJson(english = it, german = listOf("x"), unit = 1, page = 5, type = JsonPrimitive("noun"))
            }
        )

        val plan = VocabularyImporter.plan(file, listOf(bothSet, onlyPage, deleted), emptyList(), now, exampleBook = true)

        assertEquals(3, plan.skippedVocabulary)
        val completed = plan.completedVocabulary.single()
        assertEquals("b", completed.id)
        assertEquals(998, completed.page)
        assertEquals(WordType.NOUN, completed.wordType)
    }

    @Test
    fun newEntriesTakeTheCoreWordAndIgnoreMultipleChoiceOnly() {
        val parsed = VocTrainerJson.parseImport(
            """{"format": "voctrainer-import", "version": 2, "bookId": "b", "bookTitle": "Buch", "entries": [
                {"english": "market stall", "german": ["Marktstand"], "unit": 1, "coreWord": "market"},
                {"english": "every Monday/Tuesday/…", "german": ["x"], "unit": 1, "coreWord": "Monday",
                 "multipleChoiceOnly": true}]}"""
        )
        val plan = VocabularyImporter.plan(parsed, emptyList(), emptyList(), now)
        assertEquals(listOf("market", "Monday"), plan.newVocabulary.map { it.coreWord })
        assertEquals(listOf(false, false), plan.newVocabulary.map { it.choiceOnly })
        assertTrue(plan.newVocabulary.all { it.toQuizWord().isTypeable })
    }

    private fun stall(coreWord: String? = null) =
        VocabularyJson("market stall", listOf("Marktstand"), unit = 1, page = 5, type = JsonPrimitive("noun"), coreWord = coreWord)

    private val storedStall = Vocabulary(
        id = "a", english = "market stall", germanMeanings = listOf("Marktstand"), unit = 1, page = 5,
        wordType = WordType.NOUN, leitnerLevel = 3, highestLevel = 4, updatedAt = 7L, lastReviewedAt = 6L,
        firstTypedCorrectAt = 5L
    )

    @Test
    fun reImportTakesOverTheClassificationWithoutTouchingProgress() {
        val choiceOnly = Vocabulary(
            id = "c", english = "wait", germanMeanings = listOf("warten"), unit = 1, page = 5,
            wordType = WordType.NOUN, choiceOnly = true, updatedAt = 7L
        )
        val incoming = listOf(
            stall("market"),
            VocabularyJson("wait", listOf("warten"), unit = 1, page = 5, type = JsonPrimitive("noun"), coreWord = "wait")
        )

        val plan = VocabularyImporter.plan(file(incoming), listOf(storedStall, choiceOnly), emptyList(), now)

        assertEquals(2, plan.skippedVocabulary)
        assertEquals(VocabularyImportResult(0, 2, 0, 0, vocabularyCompleted = 0, vocabularyClassified = 2, vocabularyUpdated = 2), plan.result)
        val byId = plan.completedVocabulary.associateBy { it.id }
        assertEquals(storedStall.copy(coreWord = "market", updatedAt = now), byId.getValue("a"))
        assertEquals(choiceOnly.copy(coreWord = "wait", choiceOnly = false, updatedAt = now), byId.getValue("c"))
    }

    @Test
    fun reImportChangesAnExistingCoreWordAndClearsMultipleChoiceOnly() {
        val classified = storedStall.copy(coreWord = "stall")

        val changed = VocabularyImporter.plan(file(listOf(stall("market"))), listOf(classified), emptyList(), now)
        assertEquals("market", changed.completedVocabulary.single().coreWord)

        val typeable = VocabularyImporter.plan(file(listOf(stall("market"))), listOf(classified.copy(choiceOnly = true)), emptyList(), now)
        assertEquals(classified.copy(coreWord = "market", choiceOnly = false, updatedAt = now), typeable.completedVocabulary.single())

        // Same core word: only the old mark is cleared.
        val cleared = VocabularyImporter.plan(file(listOf(stall("stall"))), listOf(classified.copy(choiceOnly = true)), emptyList(), now)
        assertEquals(classified.copy(updatedAt = now), cleared.completedVocabulary.single())
        assertEquals(1, cleared.result.vocabularyClassified)
    }

    @Test
    fun excludedReImportWithoutCoreWordKeepsTheStoredOne() {
        // Only excluded entries may come without a core word; the stored one stays.
        val classified = storedStall.copy(coreWord = "stall")
        val incoming = listOf(stall().copy(excluded = true))

        val plan = VocabularyImporter.plan(file(incoming), listOf(classified), emptyList(), now)

        assertEquals(VocabularyImportResult(0, 1, 0, 0, vocabularyExcluded = 1), plan.result)
        assertEquals(classified.copy(isDeleted = true, updatedAt = now), plan.completedVocabulary.single())
    }

    @Test
    fun reImportClassifiesAndRestoresIgnoredEntries() {
        val ignored = storedStall.copy(isDeleted = true)

        val plan = VocabularyImporter.plan(file(listOf(stall("market"))), listOf(ignored), emptyList(), now)

        assertEquals(storedStall.copy(coreWord = "market", updatedAt = now), plan.completedVocabulary.single())
        assertEquals(1, plan.result.vocabularyRestored)
    }

    @Test
    fun secondImportOfTheSameFileChangesNothing() {
        val incoming = file(listOf(stall("market"), entry("old", 1).copy(excluded = true)))
        val first = VocabularyImporter.plan(incoming, listOf(storedStall), emptyList(), now)
        val stored = listOf(first.completedVocabulary.single()) + first.newVocabulary

        val second = VocabularyImporter.plan(incoming, stored, emptyList(), now + 1)

        assertEquals(VocabularyImportResult(0, 2, 0, 0), second.result)
        assertTrue(second.completedVocabulary.isEmpty())
    }

    @Test
    fun newExcludedEntriesAreCreatedIgnoredAndOthersActive() {
        val plan = VocabularyImporter.plan(
            file(
                listOf(
                    entry("old", 1).copy(excluded = true, excludedReason = "veraltet"),
                    entry("flag false", 1).copy(excluded = false),
                    entry("flag missing", 1),
                    entry("e.g.", 1).copy(excluded = true, coreWord = "e.g.")
                )
            ),
            emptyList(), emptyList(), now
        )

        assertEquals(listOf(true, false, false, true), plan.newVocabulary.map { it.isDeleted })
        assertEquals("e.g.", plan.newVocabulary.last().coreWord)
        assertEquals(VocabularyImportResult(4, 0, 0, 0, vocabularyIgnored = 2), plan.result)
    }

    @Test
    fun excludedIgnoresExistingEntriesAndItsAbsenceRestoresThem() {
        val active = storedStall.copy(coreWord = "stall")
        val ignored = storedStall.copy(id = "b", english = "bus", coreWord = "bus", isDeleted = true)
        val ignoredToo = storedStall.copy(id = "c", english = "car", coreWord = "car", isDeleted = true)
        val incoming = listOf(
            stall().copy(excluded = true),
            VocabularyJson("bus", listOf("Marktstand"), unit = 1, page = 5, type = JsonPrimitive("noun"), coreWord = "bus", excluded = false),
            VocabularyJson("car", listOf("Marktstand"), unit = 1, page = 5, type = JsonPrimitive("noun"), coreWord = "car")
        )

        val plan = VocabularyImporter.plan(file(incoming), listOf(active, ignored, ignoredToo), emptyList(), now)

        assertEquals(VocabularyImportResult(0, 3, 0, 0, vocabularyExcluded = 1, vocabularyRestored = 2), plan.result)
        val byId = plan.completedVocabulary.associateBy { it.id }
        assertEquals(active.copy(isDeleted = true, updatedAt = now), byId.getValue("a"))
        assertEquals(ignored.copy(isDeleted = false, updatedAt = now), byId.getValue("b"))
        assertEquals(ignoredToo.copy(isDeleted = false, updatedAt = now), byId.getValue("c"))
    }

    private val learned = Vocabulary(
        id = "l", english = "market stall", germanMeanings = listOf("Marktstand"), unit = 1, unitTitle = "Old title",
        part = "A", extra = "old extra", page = 5, wordType = WordType.NOUN, coreWord = "stall",
        leitnerLevel = 4, highestLevel = 5, updatedAt = 7L, lastReviewedAt = 6L, firstTypedCorrectAt = 5L
    )

    @Test
    fun reImportOverwritesEveryFieldWithTheFileButNeverTheProgress() {
        val incoming = VocabularyJson(
            "Market  Stall", listOf("Marktbude", "Stand"), unit = 1, unitTitle = "New title", part = "B",
            extra = "new extra", page = 9, type = JsonPrimitive("phrase"), coreWord = "market"
        )

        val plan = VocabularyImporter.plan(file(listOf(incoming)), listOf(learned), emptyList(), now)

        assertEquals(
            learned.copy(
                germanMeanings = listOf("Marktbude", "Stand"), unitTitle = "New title", part = "B", extra = "new extra",
                page = 9, wordType = WordType.PHRASE, coreWord = "market", updatedAt = now
            ),
            plan.completedVocabulary.single()
        )
        assertEquals(
            VocabularyImportResult(0, 1, 0, 0, vocabularyCompleted = 1, vocabularyClassified = 1, vocabularyUpdated = 1),
            plan.result
        )
    }

    @Test
    fun eachFieldAloneIsOverwritten() {
        val same = VocabularyJson(
            "market stall", listOf("Marktstand"), unit = 1, unitTitle = "Old title", part = "A",
            extra = "old extra", page = 5, type = JsonPrimitive("noun"), coreWord = "stall"
        )
        assertTrue(VocabularyImporter.plan(file(listOf(same)), listOf(learned), emptyList(), now).completedVocabulary.isEmpty())
        val changes = listOf(
            same.copy(german = listOf("Bude")) to learned.copy(germanMeanings = listOf("Bude")),
            same.copy(unitTitle = "T") to learned.copy(unitTitle = "T"),
            same.copy(part = "C") to learned.copy(part = "C"),
            same.copy(page = 6) to learned.copy(page = 6),
            same.copy(extra = "e") to learned.copy(extra = "e"),
            same.copy(type = JsonPrimitive("other")) to learned.copy(wordType = WordType.OTHER),
            same.copy(coreWord = "market") to learned.copy(coreWord = "market")
        )
        changes.forEach { (incoming, expected) ->
            val plan = VocabularyImporter.plan(file(listOf(incoming)), listOf(learned), emptyList(), now)
            assertEquals(expected.copy(updatedAt = now), plan.completedVocabulary.single())
            assertEquals(1, plan.result.vocabularyUpdated)
        }
    }

    @Test
    fun absentOrBlankOptionalFieldsClearTheStoredValues() {
        val incoming = VocabularyJson(
            "market stall", listOf("Marktstand"), unit = 1, unitTitle = "  ", part = "", extra = null, page = null,
            type = JsonPrimitive("unknown"), coreWord = "stall"
        )

        val plan = VocabularyImporter.plan(file(listOf(incoming)), listOf(learned), emptyList(), now)

        assertEquals(
            learned.copy(unitTitle = null, part = null, extra = null, page = null, wordType = null, updatedAt = now),
            plan.completedVocabulary.single()
        )
    }

    @Test
    fun anActiveEntryWithoutCoreWordLosesTheStoredOneAndIsIgnoredIfNoneCanBeDerived() {
        val incoming = VocabularyJson("market stall", listOf("Marktstand"), unit = 1, unitTitle = "Old title", part = "A",
            extra = "old extra", page = 5, type = JsonPrimitive("noun"))

        val followed = VocabularyImporter.plan(file(listOf(incoming)), listOf(learned), emptyList(), now).completedVocabulary.single()

        assertEquals(learned.copy(coreWord = null, updatedAt = now), followed)
        // The check after every import ignores it, since "market stall" has two candidates.
        val normalized = CoreWords.normalize(followed)
        assertTrue(normalized.isDeleted)
        assertEquals(4, normalized.leitnerLevel)
    }

    @Test
    fun theFirstOfEqualEntriesInTheFileWins() {
        val incoming = listOf(stall("market"), stall("stall").copy(german = listOf("Bude")))

        val plan = VocabularyImporter.plan(file(incoming), listOf(storedStall), emptyList(), now)

        assertEquals(2, plan.skippedVocabulary)
        assertEquals(storedStall.copy(coreWord = "market", updatedAt = now), plan.completedVocabulary.single())
    }

    @Test
    fun aSecondReImportOfAChangedFileChangesNothing() {
        val incoming = file(listOf(stall("market").copy(german = listOf("Bude"), page = 8), entry("bus", 1).copy(excluded = true)))
        val bus = storedStall.copy(id = "b", english = "bus", coreWord = "bus")
        val first = VocabularyImporter.plan(incoming, listOf(storedStall, bus), emptyList(), now)
        assertEquals(2, first.completedVocabulary.size)

        val second = VocabularyImporter.plan(incoming, first.completedVocabulary, emptyList(), now + 1)

        assertEquals(VocabularyImportResult(0, 2, 0, 0), second.result)
    }

    /** [VocabularyRepository.ensureExampleBook]: the example book never undoes what the learner changed. */
    @Test
    fun theExampleBookPathIsUnchanged() {
        val first = VocabularyImporter.plan(exampleFile(), emptyList(), emptyList(), now, exampleBook = true)
        val words = first.newVocabulary
        val ignoredByLearner = words[0].id
        val changedMeaning = words[1].id
        val stored = words.map {
            when (it.id) {
                ignoredByLearner -> it.copy(isDeleted = true, leitnerLevel = 3)
                changedMeaning -> it.copy(germanMeanings = listOf("eigene Bedeutung"), page = 1, part = "Z")
                else -> it
            }
        }
        val changedFile = exampleFile().let { file ->
            file.copy(entries = file.entries.mapIndexed { index, entry -> if (index == 2) entry.copy(excluded = true) else entry })
        }

        listOf(exampleFile(), changedFile).forEach { source ->
            val again = VocabularyImporter.plan(source, stored, first.newVerbs, now + 1, exampleBook = true)
            assertEquals(VocabularyImportResult(0, 150, 0, 0), again.result)
            assertTrue(again.completedVocabulary.isEmpty())
        }
        // A normal import of the same file makes the entries follow it.
        val normal = VocabularyImporter.plan(changedFile, stored, first.newVerbs, now + 1)
        assertEquals(VocabularyImportResult(0, 150, 0, 0, vocabularyCompleted = 1, vocabularyUpdated = 1, vocabularyExcluded = 1, vocabularyRestored = 1), normal.result)
        val byId = normal.completedVocabulary.associateBy { it.id }
        assertEquals(words[1].copy(updatedAt = now + 1), byId.getValue(changedMeaning))
        assertEquals(false, byId.getValue(ignoredByLearner).isDeleted)
        assertEquals(3, byId.getValue(ignoredByLearner).leitnerLevel)
        assertEquals(true, byId.getValue(words[2].id).isDeleted)
    }

    @Test
    fun fillsMissingVerbPageOnly() {
        val go = IrregularVerb(
            id = "g", infinitive = "(to) go", simplePast = "went", germanMeanings = listOf("gehen"), unit = 1, page = 300
        )
        val catch = IrregularVerb(
            id = "c", infinitive = "(to) catch", simplePast = "caught", germanMeanings = listOf("fangen"), unit = 1
        )
        val file = VocTrainerFile(
            format = VocTrainerJson.IMPORT_FORMAT, version = 1,
            irregularVerbs = listOf("(to) go", "(to) catch").map {
                IrregularVerbJson(infinitive = it, simplePast = "x", german = listOf("x"), unit = 1, page = 151)
            }
        )

        val plan = VocabularyImporter.plan(file, emptyList(), listOf(go, catch), now)

        assertEquals(listOf("c" to 151), plan.updatedVerbs.map { it.id to it.page })
    }
}
