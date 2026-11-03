package io.github.markushornbach.voctrainer.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VocabularyRepositoryTest {
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val DAY = "2026-10-09"
        const val DEVICE = "device-a"

        /** The active book of every test database; the import files belong to it. */
        const val B = "b"
        val BOOK = Book(B, "Buch", "en", isActive = true)
        const val RESTART_SETTINGS = "test_repo_restart_settings"
        const val RESTART_DEVICE = "test_repo_restart_device"
    }

    private val importFile = """
        {
          "format": "voctrainer-import", "version": 2, "bookId": "b", "bookTitle": "Buch",
          "entries": [
            {"english": "bag", "german": ["Tasche", "Tüte"], "unit": 1, "part": "A", "extra": "Put the apples in the bag.", "coreWord": "bag"},
            {"english": "(to) climb trees", "german": ["auf Bäume klettern"], "unit": 1, "coreWord": "trees"}
          ],
          "irregularVerbs": [
            {"infinitive": "(to) catch", "simplePast": "caught", "pastParticiple": null,
             "german": ["fangen"], "unit": 1, "part": "B", "germanAdded": false}
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            VocTrainerDatabase::class.java
        ).build()
        runBlocking { db.bookDao().insert(BOOK) }
        repository = VocabularyRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun importingTwiceImportsNothingTheSecondTime() = runBlocking {
        assertEquals(VocabularyImportResult(2, 0, 1, 0), repository.importVocabulary(importFile).counts)
        assertEquals(VocabularyImportResult(0, 2, 0, 0), repository.importVocabulary(importFile).counts)

        val stored = db.vocabularyDao().getAllIncludingDeleted().associateBy { it.english }
        assertEquals(2, stored.size)
        assertEquals(listOf("Tasche", "Tüte"), stored.getValue("bag").germanMeanings)
        assertEquals("Put the apples in the bag.", stored.getValue("bag").extra)
        assertEquals(1, db.irregularVerbDao().getAllIncludingDeleted().size)
    }

    private val classifiedFile = """
        {
          "format": "voctrainer-import", "version": 2, "bookId": "b", "bookTitle": "Buch",
          "entries": [
            {"english": "market stall", "german": ["Marktstand"], "unit": 1, "coreWord": "market"},
            {"english": "wait", "german": ["warten"], "unit": 1, "coreWord": "wait", "multipleChoiceOnly": false},
            {"english": "e.g.", "german": ["zum Beispiel"], "unit": 1, "type": "other", "coreWord": "e.g."},
            {"english": "old word", "german": ["alt"], "unit": 1, "excluded": true, "excludedReason": "veraltet"}
          ]
        }
    """.trimIndent()

    @Test
    fun importStoresExcludedEntriesAsIgnoredAndTakesOverClassificationOnReImport() = runBlocking {
        val first = repository.importVocabulary(importFile).counts
        assertEquals(2, first.vocabularyImported)
        val dao = db.vocabularyDao()
        val bag = dao.getAllIncludingDeleted().first { it.english == "bag" }
        dao.update(bag.copy(leitnerLevel = 4, highestLevel = 4, lastReviewedAt = 99L, choiceOnly = true))

        val fileWithStop = classifiedFile.replace(
            "\"entries\": [", "\"entries\": [{\"english\": \"bag\", \"german\": [\"Tasche\"], \"unit\": 1, \"coreWord\": \"bag\"},"
        )
        val result = repository.importVocabulary(fileWithStop).counts

        assertEquals(VocabularyImportResult(4, 1, 0, 0, vocabularyIgnored = 1, vocabularyClassified = 1, vocabularyUpdated = 1), result)
        val stored = dao.getAllIncludingDeleted().associateBy { it.english }
        assertEquals(true, stored.getValue("old word").isDeleted)
        assertEquals(false, stored.getValue("market stall").isDeleted)
        assertEquals("e.g.", stored.getValue("e.g.").coreWord)
        val changed = stored.getValue("bag")
        assertEquals("bag", changed.coreWord)
        assertEquals(false, changed.choiceOnly)
        assertEquals(bag.id, changed.id)
        assertEquals(4, changed.leitnerLevel)
        assertEquals(4, changed.highestLevel)
        assertEquals(99L, changed.lastReviewedAt)

        val again = repository.importVocabulary(fileWithStop).counts
        assertEquals(VocabularyImportResult(0, 5, 0, 0), again)
        assertEquals(stored, dao.getAllIncludingDeleted().associateBy { it.english })
    }

    @Test
    fun excludedFlagIgnoresAndItsAbsenceRestoresExistingEntries() = runBlocking {
        repository.importVocabulary(classifiedFile)
        val dao = db.vocabularyDao()
        val before = dao.getAllIncludingDeleted().associateBy { it.english }
        // A non-excluded entry needs a core word in the file.
        val flipped = classifiedFile.replace("\"excluded\": true", "\"excluded\": false, \"coreWord\": \"old\"")
            .replace("{\"english\": \"wait\"", "{\"excluded\": true, \"english\": \"wait\"")

        repository.importVocabulary(flipped)

        val after = dao.getAllIncludingDeleted().associateBy { it.english }
        assertEquals(before.mapValues { it.key != "old word" && it.value.isDeleted } - "wait", after.mapValues { it.value.isDeleted } - "wait")
        assertTrue(after.getValue("wait").isDeleted)
        assertFalse(after.getValue("old word").isDeleted)
        assertEquals("old", after.getValue("old word").coreWord)
        assertEquals(before.mapValues { it.value.leitnerLevel }, after.mapValues { it.value.leitnerLevel })
        assertEquals(before.mapValues { it.value.id }, after.mapValues { it.value.id })
    }

    @Test
    fun laterFileFillsMissingVerbFormWithoutResettingProgress() = runBlocking {
        repository.importVocabulary(importFile)
        val catch = db.irregularVerbDao().getAllIncludingDeleted().single()
        db.irregularVerbDao().update(catch.copy(leitnerLevel = 4))

        val result = repository.importVocabulary(importFile.replace("\"pastParticiple\": null", "\"pastParticiple\": \"caught\"")).counts

        assertEquals(VocabularyImportResult(0, 2, 0, 1), result)
        val updated = db.irregularVerbDao().getAllIncludingDeleted().single()
        assertEquals("caught", updated.pastParticiple)
        assertEquals(4, updated.leitnerLevel)
        assertEquals(catch.id, updated.id)
    }

    @Test
    fun invalidFileChangesNothing() = runBlocking {
        val broken = importFile.replace("\"german\": [\"auf Bäume klettern\"]", "\"german\": []")

        assertThrows(InvalidFileException::class.java) { runBlocking { repository.importVocabulary(broken) } }

        assertTrue(db.vocabularyDao().getAllIncludingDeleted().isEmpty())
        assertTrue(db.irregularVerbDao().getAllIncludingDeleted().isEmpty())
    }

    @Test
    fun manualEntryRejectsDuplicateEnglishAndUnit() = runBlocking {
        val bag = Vocabulary(english = "bag", germanMeanings = listOf("Tasche"), unit = 1)

        assertTrue(repository.addVocabulary(bag))
        assertFalse(repository.addVocabulary(bag.copy(id = "other", english = " Bag ")))
        assertTrue(repository.addVocabulary(bag.copy(id = "unit2", unit = 2)))
    }

    @Test
    fun firstAttemptChangesLevelAndUpdatedAt() = runBlocking {
        val word = Vocabulary(id = "w", english = "bag", germanMeanings = listOf("Tasche"), unit = 1, leitnerLevel = 2)
        repository.addVocabulary(word)
        val before = db.vocabularyDao().getById("w")!!.updatedAt
        Thread.sleep(5)

        repository.recordFirstAttempt("w", correct = true, early = false, date = DAY, deviceId = DEVICE)
        val afterCorrect = db.vocabularyDao().getById("w")!!
        assertEquals(3, afterCorrect.leitnerLevel)
        assertTrue(afterCorrect.updatedAt > before)

        repository.recordFirstAttempt("w", correct = false, early = false, date = DAY, deviceId = DEVICE)
        repository.recordFirstAttempt("w", correct = false, early = false, date = DAY, deviceId = DEVICE)
        repository.recordFirstAttempt("w", correct = false, early = false, date = DAY, deviceId = DEVICE)
        repository.recordFirstAttempt("w", correct = false, early = false, date = DAY, deviceId = DEVICE)
        assertEquals(0, db.vocabularyDao().getById("w")!!.leitnerLevel)
    }

    @Test
    fun verbFirstAttemptChangesOnlyTheVerbLevel() = runBlocking {
        repository.importVocabulary(importFile)
        val catch = db.irregularVerbDao().getAllIncludingDeleted().single()
        Thread.sleep(5)

        repository.recordVerbFirstAttempt(catch.id, correct = true, early = false, date = DAY, deviceId = DEVICE)
        val afterCorrect = db.irregularVerbDao().getById(catch.id)!!
        assertEquals(1, afterCorrect.leitnerLevel)
        assertTrue(afterCorrect.updatedAt > catch.updatedAt)
        assertTrue(db.vocabularyDao().getAllIncludingDeleted().all { it.leitnerLevel == 0 })

        repository.recordVerbFirstAttempt(catch.id, correct = false, early = false, date = DAY, deviceId = DEVICE)
        repository.recordVerbFirstAttempt(catch.id, correct = false, early = false, date = DAY, deviceId = DEVICE)
        assertEquals(0, db.irregularVerbDao().getById(catch.id)!!.leitnerLevel)
    }

    @Test
    fun ignoredEntryIsNotQuizzedAndRestoredWithItsLevel() = runBlocking {
        repository.importVocabulary(importFile)
        val bag = db.vocabularyDao().getAllIncludingDeleted().single { it.english == "bag" }
        db.vocabularyDao().update(bag.copy(leitnerLevel = 4))
        Thread.sleep(5)

        repository.setIgnored(bag.id, ignored = true)
        val ignored = db.vocabularyDao().getById(bag.id)!!
        assertTrue(ignored.isDeleted)
        assertTrue(ignored.updatedAt > bag.updatedAt)
        assertFalse(repository.vocabularyInInsertionOrder.first().any { it.id == bag.id })
        assertEquals(1, repository.activeVocabularyCount.first())
        assertTrue(repository.allVocabularyIncludingIgnored.first().any { it.id == bag.id })
        Thread.sleep(5)

        repository.setIgnored(bag.id, ignored = false)
        val restored = db.vocabularyDao().getById(bag.id)!!
        assertEquals(ignored.copy(isDeleted = false, updatedAt = restored.updatedAt), restored)
        assertEquals(4, restored.leitnerLevel)
        assertTrue(restored.updatedAt > ignored.updatedAt)
        assertTrue(repository.vocabularyInInsertionOrder.first().any { it.id == bag.id })

        // The import file is the reference: an entry it does not exclude becomes active again.
        repository.setIgnored(bag.id, ignored = true)
        val again = repository.importVocabulary(importFile).counts
        assertEquals(0, again.vocabularyImported)
        assertEquals(1, again.vocabularyRestored)
        assertEquals(1, db.vocabularyDao().getAllIncludingDeleted().count { it.english == "bag" })
        assertEquals(restored.copy(updatedAt = db.vocabularyDao().getById(bag.id)!!.updatedAt), db.vocabularyDao().getById(bag.id))
    }

    @Test
    fun editorListKeepsInsertionOrderIncludingIgnoredEntries() = runBlocking {
        repository.importVocabulary(importFile)
        val first = db.vocabularyDao().getAllIncludingDeleted().single { it.english == "bag" }
        repository.setIgnored(first.id, ignored = true)
        assertEquals(listOf("bag", "(to) climb trees"), repository.allVocabularyIncludingIgnored.first().map { it.english })
    }

    @Test
    fun firstAttemptSetsTheReviewDateAndAnEarlyCorrectAnswerKeepsTheLevel() = runBlocking {
        val word = Vocabulary(id = "w", english = "bag", germanMeanings = listOf("Tasche"), unit = 1, leitnerLevel = 2)
        repository.addVocabulary(word)
        val before = System.currentTimeMillis()

        repository.recordFirstAttempt("w", correct = true, early = true, date = DAY, deviceId = DEVICE)
        val early = db.vocabularyDao().getById("w")!!
        assertEquals(3, early.leitnerLevel)
        assertTrue(early.lastReviewedAt!! >= before)

        repository.recordFirstAttempt("w", correct = false, early = true, date = DAY, deviceId = DEVICE)
        assertEquals(2, db.vocabularyDao().getById("w")!!.leitnerLevel)

        // Editing by hand keeps the review date.
        val reviewed = db.vocabularyDao().getById("w")!!
        repository.update(reviewed.copy(leitnerLevel = 0))
        assertEquals(reviewed.lastReviewedAt, db.vocabularyDao().getById("w")!!.lastReviewedAt)

        repository.importVocabulary(importFile)
        val catch = db.irregularVerbDao().getAllIncludingDeleted().single()
        repository.recordVerbFirstAttempt(catch.id, correct = true, early = false, date = DAY, deviceId = DEVICE)
        val verb = db.irregularVerbDao().getById(catch.id)!!
        assertEquals(1, verb.leitnerLevel)
        assertTrue(verb.lastReviewedAt!! >= before)
    }

    @Test
    fun levelFourComesAtTheEarliestOnTheNextDay() = runBlocking {
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.now(zone)
        repository.addVocabulary(Vocabulary(id = "w", english = "bag", germanMeanings = listOf("Tasche"), unit = 1, leitnerLevel = 2, coreWord = "bag"))

        repository.recordFirstAttempt("w", correct = true, early = true, date = today.toString(), deviceId = DEVICE, zone = zone)
        assertEquals(3, db.vocabularyDao().getById("w")!!.leitnerLevel)
        repository.recordFirstAttempt("w", correct = true, early = true, date = today.toString(), deviceId = DEVICE, zone = zone)
        assertEquals(3, db.vocabularyDao().getById("w")!!.leitnerLevel)

        // The review date is now today; one day later the word may rise to 4, even when early.
        // A typeable word (core word) reaches level 4 by typing, not by multiple choice.
        repository.recordFirstAttempt("w", correct = true, early = true, date = today.plusDays(1).toString(), deviceId = DEVICE, zone = zone, form = io.github.markushornbach.voctrainer.learning.AnswerForm.TYPED)
        assertEquals(4, db.vocabularyDao().getById("w")!!.leitnerLevel)
    }

    @Test
    fun everyPhraseFollowsTheNormalLadder() = runBlocking {
        // Every phrase has a core word, also one with the unused flag choiceOnly.
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.now(zone)
        val typed = io.github.markushornbach.voctrainer.learning.AnswerForm.TYPED
        val phrase = io.github.markushornbach.voctrainer.learning.WordType.PHRASE
        repository.addVocabulary(Vocabulary(id = "p", english = "Have you ever …?", germanMeanings = listOf("Hast du jemals …?"), unit = 1, leitnerLevel = 2, wordType = phrase, coreWord = "ever"))
        repository.addVocabulary(Vocabulary(id = "u", english = "No way!", germanMeanings = listOf("Auf keinen Fall!"), unit = 1, leitnerLevel = 2, wordType = phrase, coreWord = "way"))
        fun level(id: String) = runBlocking { db.vocabularyDao().getById(id)!!.leitnerLevel }

        repository.recordFirstAttempt("p", correct = true, early = false, date = today.toString(), deviceId = DEVICE, zone = zone, form = typed)
        repository.recordFirstAttempt("u", correct = true, early = false, date = today.toString(), deviceId = DEVICE, zone = zone)
        assertEquals(3, level("p"))
        assertEquals(3, level("u"))
        // On a later day both rise to 4 by typing; nothing jumps to 5.
        repository.recordFirstAttempt("p", correct = true, early = false, date = today.plusDays(1).toString(), deviceId = DEVICE, zone = zone, form = typed)
        repository.recordFirstAttempt("u", correct = true, early = false, date = today.plusDays(1).toString(), deviceId = DEVICE, zone = zone, form = typed)
        assertEquals(4, level("p"))
        assertEquals(4, level("u"))
        // Multiple choice keeps a phrase at most on level 3 ("Mit Vorschlägen").
        repository.recordFirstAttempt("p", correct = true, early = false, date = today.plusDays(30).toString(), deviceId = DEVICE, zone = zone)
        assertEquals(3, level("p"))
        // Due again: typed to 5, and a wrong answer on 5 drops one level.
        repository.recordFirstAttempt("u", correct = true, early = false, date = today.plusDays(30).toString(), deviceId = DEVICE, zone = zone, form = typed)
        assertEquals(5, level("u"))
        repository.recordFirstAttempt("u", correct = false, early = false, date = today.plusDays(60).toString(), deviceId = DEVICE, zone = zone, form = typed)
        assertEquals(4, level("u"))
    }

    // --- Every active entry has a core word ---

    /** Entries without word type or core word and with the unused flag choiceOnly (possible in a backup). */
    private suspend fun insertOldEntries() {
        db.vocabularyDao().insertAll(
            listOf(
                Vocabulary(id = "w", english = "(to) walk", germanMeanings = listOf("gehen"), unit = 1, leitnerLevel = 3, highestLevel = 4, lastReviewedAt = 50L, updatedAt = 1_000L),
                Vocabulary(id = "p", english = "care for sb.", germanMeanings = listOf("jn. pflegen"), unit = 1, leitnerLevel = 4, highestLevel = 4, updatedAt = 1_000L),
                Vocabulary(id = "c", english = "Hello!", germanMeanings = listOf("Hallo!"), unit = 1, leitnerLevel = 5, highestLevel = 5, choiceOnly = true, updatedAt = 1_000L),
                Vocabulary(id = "i", english = "a lot of", germanMeanings = listOf("viel"), unit = 1, leitnerLevel = 2, highestLevel = 2, isDeleted = true, choiceOnly = true, updatedAt = 1_000L)
            ).map { it.copy(bookId = B) }
        )
    }

    @Test
    fun ensureCoreWordsDerivesIgnoresAndKeepsProgressAndUpdatedAt() = runBlocking {
        insertOldEntries()
        val before = db.vocabularyDao().getAllIncludingDeleted().associateBy { it.id }

        assertEquals(1, repository.ensureCoreWords())

        val after = db.vocabularyDao().getAllIncludingDeleted().associateBy { it.id }
        assertEquals(before.getValue("w").copy(coreWord = "walk"), after.getValue("w"))
        assertEquals(before.getValue("p").copy(isDeleted = true), after.getValue("p"))
        assertEquals(before.getValue("c").copy(coreWord = "Hello", choiceOnly = false), after.getValue("c"))
        assertEquals(before.getValue("i"), after.getValue("i"))
        assertTrue(after.values.all { it.updatedAt == 1_000L })
        assertTrue(repository.vocabularyInInsertionOrder.first().all { it.toQuizWord().isTypeable })
        // Idempotent.
        assertEquals(0, repository.ensureCoreWords())
        assertEquals(after, db.vocabularyDao().getAllIncludingDeleted().associateBy { it.id })
    }

    @Test
    fun importOfAFileWithoutCoreWordsIsRejectedAndChangesNothing() = runBlocking {
        repository.importVocabulary(importFile)
        val before = db.vocabularyDao().getAllIncludingDeleted()
        val verbsBefore = db.irregularVerbDao().getAllIncludingDeleted()
        val old = importFile.replace(", \"coreWord\": \"bag\"", "").replace(", \"coreWord\": \"trees\"", "")
            .replace("\"bag\"", "\"bus\"")

        val error = assertThrows(InvalidFileException::class.java) { runBlocking { repository.importVocabulary(old) } }

        assertTrue(error.message!!.startsWith("In der Datei fehlt bei 2 Vokabeln das Wort zum Tippen"))
        assertEquals(before, db.vocabularyDao().getAllIncludingDeleted())
        assertEquals(verbsBefore, db.irregularVerbDao().getAllIncludingDeleted())
    }

    @Test
    fun surePhrasesCountForTheFigureAndTheBadges() = runBlocking {
        // A full avatar: 375 askable entries (355 of them filler), 10 sure: 9 words and 1 phrase.
        addFiller(355)
        (1..10).forEach {
            repository.addVocabulary(Vocabulary(id = "n$it", english = "new$it", germanMeanings = listOf("N$it"), unit = 1))
        }
        (1..9).forEach {
            repository.addVocabulary(Vocabulary(id = "s$it", english = "word$it", germanMeanings = listOf("W$it"), unit = 1, leitnerLevel = 4))
        }
        storeOldStartPoints(DAY, DEVICE, 330)
        repository.chooseFigure(io.github.markushornbach.voctrainer.learning.FigureKind.DRAGON)
        assertEquals(2, db.figureDao().getAll().single().stage)
        assertTrue(repository.awardBadges(longestStreak = 0).none { it.series == io.github.markushornbach.voctrainer.learning.BadgeSeries.SURE_WORDS })

        repository.addVocabulary(
            Vocabulary(id = "p", english = "Have you ever …?", germanMeanings = listOf("Hast du jemals …?"), unit = 1,
                leitnerLevel = 4, wordType = io.github.markushornbach.voctrainer.learning.WordType.PHRASE, coreWord = "ever")
        )
        assertEquals(3, repository.updateFigureStage()!!.stage)
        assertEquals(
            listOf(io.github.markushornbach.voctrainer.learning.BadgeKey(io.github.markushornbach.voctrainer.learning.BadgeSeries.SURE_WORDS, 1)),
            repository.awardBadges(longestStreak = 0)
        )
    }

    @Test
    fun firstAttemptsStoreRisePointsWithTheLevel() = runBlocking {
        // highestLevel = 2 like a word that really got to level 2; only level 3 is new (3 points).
        val word = Vocabulary(id = "w", english = "bag", germanMeanings = listOf("Tasche"), unit = 1, leitnerLevel = 2, highestLevel = 2, coreWord = "bag")
        repository.addVocabulary(word)

        assertEquals(FirstAttemptResult(3, rose = true), repository.recordFirstAttempt("w", correct = true, early = false, date = DAY, deviceId = DEVICE).copy(outcome = null))
        // Level 3 was reviewed today, so the next answer cannot reach 4 on the same day.
        assertEquals(FirstAttemptResult(0, rose = false), repository.recordFirstAttempt("w", correct = true, early = true, date = DAY, deviceId = DEVICE).copy(outcome = null))
        assertEquals(FirstAttemptResult(0, rose = false), repository.recordFirstAttempt("w", correct = false, early = false, date = DAY, deviceId = DEVICE).copy(outcome = null))
        repository.importVocabulary(importFile)
        val catch = db.irregularVerbDao().getAllIncludingDeleted().single()
        assertEquals(FirstAttemptResult(0, rose = true), repository.recordVerbFirstAttempt(catch.id, correct = true, early = false, date = DAY, deviceId = DEVICE).copy(outcome = null))

        // The verb answer above gave no points.
        assertEquals(3, db.pointsDayDao().get(B, DAY, DEVICE)!!.risePoints)
        assertEquals(3, repository.totalPointsNow())
        assertEquals(13, repository.recordDailyBonus(DAY, DEVICE, 13))
        assertEquals(0, repository.recordDailyBonus(DAY, DEVICE, 14))
        // Start points (possible in a backup) count once.
        storeOldStartPoints(DAY, DEVICE, 120)
        assertEquals(3 + 13 + 120, repository.totalPointsNow())
    }

    @Test
    fun chosenFigureGrowsWithPointsAndSureWords() = runBlocking {
        // 375 askable words (a full avatar), so stage 3 needs its full 10 sure words.
        addFiller(355)
        (1..20).forEach {
            repository.addVocabulary(Vocabulary(id = "n$it", english = "new$it", germanMeanings = listOf("N$it"), unit = 1))
        }
        storeOldStartPoints(DAY, DEVICE, 130)
        repository.chooseFigure(io.github.markushornbach.voctrainer.learning.FigureKind.DRAGON)
        // 130 points give stage 2 right away; stage 3 also needs sure words.
        val chosen = db.figureDao().getAll().single()
        assertEquals(0, chosen.pointsAtStart)
        assertEquals(2, chosen.stage)
        repository.chooseFigure(io.github.markushornbach.voctrainer.learning.FigureKind.MONSTER)
        assertEquals(1, db.figureDao().getAll().size)

        repository.addTestPoints(DAY, DEVICE, 200)
        assertEquals(null, repository.updateFigureStage())
        (1..12).forEach {
            repository.addVocabulary(Vocabulary(id = "s$it", english = "word$it", germanMeanings = listOf("W$it"), unit = 1, leitnerLevel = 4))
        }
        val growth = repository.updateFigureStage()!!
        assertEquals(3, growth.stage)
        val kept = db.figureDao().getAll().single()
        assertEquals("dragon", kept.kind)
        assertEquals(3, kept.stage)
    }

    /** Words nobody has learned, in another unit: make the book big enough for a full avatar. */
    private suspend fun addFiller(count: Int) = (1..count).forEach {
        repository.addVocabulary(Vocabulary(id = "f$it", english = "filler$it", germanMeanings = listOf("F$it"), unit = 2, coreWord = "filler$it"))
    }

    /** Start points as a backup may bring them (never awarded). */
    private suspend fun storeOldStartPoints(date: String, deviceId: String, points: Int) {
        val row = db.pointsDayDao().get(B, date, deviceId) ?: PointsDay(date, deviceId, updatedAt = 0, bookId = B)
        db.pointsDayDao().upsert(row.copy(startPoints = points, updatedAt = 1L))
    }

    @Test
    fun finishedRoundsAreCountedPerDayAndDevice() = runBlocking {
        repository.recordRoundCompleted("2026-10-08", "phone", 2)
        repository.recordRoundCompleted("2026-10-08", "phone", 3)
        repository.recordRoundCompleted("2026-10-08", "tablet", 1)
        repository.recordRoundCompleted("2026-10-09", "phone", 1)

        val days = repository.allPracticeDays().associateBy { it.date to it.deviceId }
        assertEquals(3, days.size)
        val phone = days.getValue("2026-10-08" to "phone")
        assertEquals(2, phone.roundsCompleted)
        assertEquals(3, phone.dailyGoal)
        assertEquals(1, days.getValue("2026-10-08" to "tablet").roundsCompleted)
    }

    private fun restartSettings(): AppSettings {
        deleteRestartSettings()
        return AppSettings(InstrumentationRegistry.getInstrumentation().targetContext, RESTART_SETTINGS, RESTART_DEVICE)
    }

    private fun deleteRestartSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteSharedPreferences(RESTART_SETTINGS)
        context.deleteSharedPreferences(RESTART_DEVICE)
    }

    /**
     * "Buch neu beginnen": everything of the active book starts from scratch in one go; finished
     * avatars, the vocabulary, the selection and the other book stay.
     */
    @Test
    fun restartingTheBookResetsItsProgressAndKeepsTheCollection() = runBlocking {
        val settings = restartSettings()
        try {
            repository.importVocabulary(importFile)
            val words = db.vocabularyDao().getAllIncludingDeleted().associateBy { it.english }
            val bag = words.getValue("bag")
            val photos = words.getValue("(to) climb trees")
            db.vocabularyDao().update(bag.copy(leitnerLevel = 4, highestLevel = 5, lastReviewedAt = 50L, firstTypedCorrectAt = 40L))
            // Ignored, with progress: reset too.
            db.vocabularyDao().update(photos.copy(leitnerLevel = 2, highestLevel = 2, isDeleted = true))
            val catch = db.irregularVerbDao().getAllIncludingDeleted().single()
            db.irregularVerbDao().update(catch.copy(leitnerLevel = 3, lastReviewedAt = 60L))
            repository.saveQuizFilter(QuizMode.VOCABULARY, QuizFilter(UnitChoice.Number(1)))
            repository.recordRoundCompleted(DAY, DEVICE, 2)
            repository.recordDailyBonus(DAY, DEVICE, 11)
            db.pointsDayDao().upsert(PointsDay(DAY, "device-b", risePoints = 4_300, updatedAt = 10L, bookId = B))
            db.badgeDao().upsertAll(listOf(Badge("sure_words", 1, 5L, B)))
            db.figureDao().upsertAll(
                listOf(
                    Figure(0, "dragon", pointsAtStart = 0, stage = 10, startedAt = 1L, finishedAt = 5L, updatedAt = 5L, bookId = B),
                    Figure(1, "monster", pointsAtStart = 4_000, stage = 2, startedAt = 6L, updatedAt = 7L, bookId = B)
                )
            )
            // Another book with progress, inactive.
            db.bookDao().insert(Book("other", "Anderes Buch", "en"))
            val otherWord = Vocabulary(
                id = "o", english = "sun", germanMeanings = listOf("Sonne"), unit = 1, leitnerLevel = 3,
                coreWord = "sun", bookId = "other"
            )
            db.vocabularyDao().insert(otherWord)
            db.pointsDayDao().upsert(PointsDay(DAY, DEVICE, risePoints = 70, updatedAt = 10L, bookId = "other"))
            db.badgeDao().upsertAll(listOf(Badge("sure_words", 1, 5L, "other")))
            assertTrue(repository.canRestartBook.first())
            val before = db.vocabularyDao().getById(bag.id)!!
            Thread.sleep(5)

            assertEquals("Buch", repository.restartBook(settings))

            val reset = db.vocabularyDao().getById(bag.id)!!
            assertEquals(
                before.copy(leitnerLevel = 0, highestLevel = 0, lastReviewedAt = null, firstTypedCorrectAt = null, updatedAt = reset.updatedAt),
                reset
            )
            assertTrue(reset.updatedAt > before.updatedAt)
            val ignored = db.vocabularyDao().getById(photos.id)!!
            assertEquals(0, ignored.leitnerLevel)
            assertTrue(ignored.isDeleted)
            val verb = db.irregularVerbDao().getById(catch.id)!!
            assertEquals(0, verb.leitnerLevel)
            assertNull(verb.lastReviewedAt)
            assertEquals(0, repository.totalPointsNow())
            assertTrue(db.badgeDao().getAll().isEmpty())
            assertEquals(listOf(0), repository.allPracticeDays().map { it.roundsCompleted })
            assertEquals(0, repository.longestStreak(DEVICE, 2))
            // The collection stays, no avatar is current: the learner chooses avatar 1 again.
            val book = repository.activeBookNow()!!
            assertEquals(2, book.figureSlotStart)
            val figures = db.figureDao().getAll()
            assertEquals(2, figures.size)
            assertNull(figures.current(book.figureSlotStart))
            assertEquals(listOf(0), figures.collection().map { it.slot })
            assertEquals(1, figures.avatarPlan(2, book.figureSlotStart).number)
            // The selection, the vocabulary and the other book stay.
            assertEquals(QuizFilter(UnitChoice.Number(1)), repository.loadQuizFilter(QuizMode.VOCABULARY))
            assertEquals(2, db.vocabularyDao().getAllIncludingDeleted().size)
            assertEquals(otherWord, db.vocabularyDao().getById("o"))
            assertEquals(70, db.pointsDayDao().getAllOfBook("other").single().risePoints)
            assertEquals(1, db.badgeDao().getAllBooks().count { it.bookId == "other" })
            assertFalse(repository.canRestartBook.first())

            // The new avatar is avatar 1 in the next slot and starts with 0 points.
            repository.chooseFigure(FigureKind.FIGHTER)
            val chosen = db.figureDao().getAll().current(book.figureSlotStart)!!
            assertEquals(2, chosen.slot)
            assertEquals("fighter", chosen.kind)
            assertEquals(0, chosen.pointsAtStart)
            assertEquals(0, chosen.stage)
        } finally {
            deleteRestartSettings()
        }
    }

    /** The restart is part of the backup; restoring a backup made before brings its own state back. */
    @Test
    fun theRestartSurvivesExportAndRestore() = runBlocking {
        val settings = restartSettings()
        try {
            repository.chooseFigure(FigureKind.DRAGON)
            repository.recordDailyBonus(DAY, DEVICE, 11)
            val oldFile = repository.exportSync()
            Thread.sleep(5)
            repository.restartBook(settings)
            val exported = repository.exportSync()

            repository.restoreBackup(exported, settings)
            assertNull(db.figureDao().getAll().current(1))
            assertEquals(1, repository.activeBookNow()!!.figureSlotStart)
            assertEquals(0, repository.totalPointsNow())
            // Restoring the old file takes its value: the old avatar is current again.
            repository.restoreBackup(oldFile, settings)
            assertEquals(0, repository.activeBookNow()!!.figureSlotStart)
            assertEquals(0, db.figureDao().getAll().current(0)!!.slot)
            assertEquals(11, repository.totalPointsNow())
        } finally {
            deleteRestartSettings()
        }
    }

    @Test
    fun withoutProgressThereIsNothingToRestart() = runBlocking {
        repository.importVocabulary(importFile)
        assertFalse(repository.canRestartBook.first())
        repository.chooseFigure(FigureKind.DRAGON)
        assertTrue(repository.canRestartBook.first())
    }
}
