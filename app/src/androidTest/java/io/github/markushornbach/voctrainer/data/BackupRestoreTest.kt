package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.AppTheme
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.SettingsBackup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** "Sicherung wiederherstellen" replaces everything; "Mit anderem Gerät abgleichen" merges. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository
    private lateinit var settings: AppSettings

    private companion object {
        // Own preference files, so the test never touches the settings of the installed app.
        const val SETTINGS = "test_backup_restore_settings"
        const val DEVICE = "test_backup_restore_device"
        const val OLD = 1_000L
        const val B = "b"
    }

    /** Everything a restore has to bring back, field by field. */
    private data class Snapshot(
        val books: List<Book>,
        val vocabulary: List<Vocabulary>,
        val verbs: List<IrregularVerb>,
        val practiceDays: List<PracticeDay>,
        val pointsDays: List<PointsDay>,
        val figures: List<Figure>,
        val settings: SettingsBackup
    )

    private suspend fun snapshot() = Snapshot(
        db.bookDao().getAll(),
        db.vocabularyDao().getAllBooksIncludingDeletedInInsertionOrder(),
        db.irregularVerbDao().getAllBooksIncludingDeletedInInsertionOrder(),
        db.practiceDayDao().getAllBooks().sortedBy { it.date + it.deviceId },
        db.pointsDayDao().getAllBooks().sortedBy { it.date + it.deviceId },
        db.figureDao().getAllBooks().sortedBy { it.slot },
        settings.toBackup()
    )

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = Room.inMemoryDatabaseBuilder(context, VocTrainerDatabase::class.java).build()
        runBlocking { db.bookDao().insert(Book(B, "Buch", "en", isActive = true)) }
        repository = VocabularyRepository(db)
        settings = AppSettings(context, SETTINGS, DEVICE)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    @Test
    fun aRestoreFinishesTheFirstStart() = runBlocking {
        val backup = repository.exportSync(settings.toBackup())
        // A new phone in the middle of its first start.
        assertFalse(settings.firstStartDone.value)
        repository.restoreBackup(backup, settings)
        assertTrue(settings.firstStartDone.value)
    }

    private suspend fun fillEverything() {
        db.vocabularyDao().insertAll(
            listOf(
                Vocabulary(
                    id = "v1", english = "bag", germanMeanings = listOf("Tasche"), unit = 1, part = "A", page = 12,
                    leitnerLevel = 3, highestLevel = 4, lastReviewedAt = 900L, firstTypedCorrectAt = 800L,
                    coreWord = "bag", updatedAt = OLD
                ),
                Vocabulary(
                    id = "v2", english = "at the weekend", germanMeanings = listOf("am Wochenende"), unit = 1,
                    leitnerLevel = 5, highestLevel = 5, lastReviewedAt = 950L, coreWord = "weekend", updatedAt = OLD
                ),
                Vocabulary(
                    id = "v3", english = "old", germanMeanings = listOf("alt"), unit = 2, leitnerLevel = 2,
                    highestLevel = 2, isDeleted = true, updatedAt = OLD
                ),
                Vocabulary(id = "v4", english = "new", germanMeanings = listOf("neu"), unit = 2, coreWord = "new", updatedAt = OLD)
            ).map { it.copy(bookId = B) }
        )
        db.irregularVerbDao().insertAll(
            listOf(
                IrregularVerb(
                    id = "i1", infinitive = "(to) go", simplePast = "went", pastParticiple = "gone",
                    germanMeanings = listOf("gehen"), unit = 1, leitnerLevel = 2, lastReviewedAt = 700L, updatedAt = OLD
                ),
                IrregularVerb(
                    id = "i2", infinitive = "(to) catch", simplePast = "caught", germanMeanings = listOf("fangen"),
                    unit = 1, leitnerLevel = 4, updatedAt = OLD
                )
            ).map { it.copy(bookId = B) }
        )
        db.practiceDayDao().upsertAll(
            listOf(PracticeDay("2026-10-01", "device-a", 2, 2, OLD, B), PracticeDay("2026-10-02", "device-b", 1, 3, OLD, B))
        )
        db.pointsDayDao().upsertAll(
            listOf(PointsDay("2026-10-01", "device-a", 30, 10, 0, OLD, B), PointsDay("2026-10-02", "device-b", 10, 0, 0, OLD, B))
        )
        // Total points 50: the current figure started at 50, so the recomputed stage stays.
        db.figureDao().upsertAll(
            listOf(
                Figure(0, "dragon", pointsAtStart = 0, stage = 10, startedAt = 100L, finishedAt = 600L, updatedAt = OLD, bookId = B),
                Figure(1, "monster", pointsAtStart = 50, stage = 1, startedAt = 650L, updatedAt = OLD, bookId = B)
            )
        )
        settings.setTheme(AppTheme.SUNNY)
        settings.setVibration(true)
        settings.setAutoSpeak(false)
        settings.setEditButtonInQuiz(true)
        settings.setQuizMode(QuizMode.VERBS)
        repository.saveQuizFilter(QuizMode.VOCABULARY, QuizFilter(UnitChoice.Number(1), setOf("A"), 10, 20))
        repository.saveQuizFilter(QuizMode.VERBS, QuizFilter(UnitChoice.Number(2)))
        settings.setDailyGoal(4)
        settings.setReminderEnabled(true)
        settings.setReminderMinutes(18 * 60 + 15)
        settings.tipUnderstood(Tip.TYPING)
        settings.tipUnderstood(Tip.ROUND_RESULT)
        settings.finishLevelCapIntro(0)
    }

    /** Changes every table and the settings after the export. */
    private suspend fun changeEverything() {
        repository.setVocabularyLevels(listOf("v1"), 0)
        repository.setVocabularyLevels(listOf("v4"), 5)
        repository.setVerbLevels(listOf("i1"), 5)
        repository.setIgnored("v2", true)
        repository.setIgnored("v3", false)
        repository.addVocabulary(Vocabulary(id = "added", english = "later", germanMeanings = listOf("später"), unit = 3, coreWord = "later"))
        repository.recordRoundCompleted("2026-10-01", "device-a", 5)
        repository.recordRoundCompleted("2026-10-05", "device-a", 5)
        repository.addTestPoints("2026-10-05", "device-a", 100)
        repository.deleteAllFiguresForTest()
        db.figureDao().upsert(Figure(0, "fighter", pointsAtStart = 0, stage = 2, startedAt = 5_000L, updatedAt = 5_000L, bookId = B))
        settings.setTheme(AppTheme.GAMING_DARK)
        settings.setVibration(false)
        settings.setAutoSpeak(true)
        settings.setEditButtonInQuiz(false)
        settings.setQuizMode(QuizMode.VOCABULARY)
        repository.saveQuizFilter(QuizMode.VOCABULARY, QuizFilter())
        settings.setDailyGoal(1)
        settings.setReminderEnabled(false)
        settings.setReminderMinutes(7 * 60)
        settings.resetTips()
        settings.setLastShownFigureStage(B, 0, 2)
        settings.setLastShownFigureStage(B, 1, 7)
    }

    @Test
    fun restoreBringsBackEveryTableAndTheSettingsExactly() = runBlocking {
        fillEverything()
        val atExport = snapshot()
        val exported = repository.exportSync(settings.toBackup())
        assertEquals(
            io.github.markushornbach.voctrainer.BuildConfig.VERSION_NAME,
            Json.parseToJsonElement(exported).jsonObject.getValue("appVersion").jsonPrimitive.content
        )

        changeEverything()
        assertNotEquals(atExport, snapshot())

        val info = repository.restoreBackup(exported, settings)

        val restored = snapshot()
        assertEquals(atExport.books, restored.books)
        assertEquals(QuizFilter(UnitChoice.Number(1), setOf("A"), 10, 20), repository.loadQuizFilter(QuizMode.VOCABULARY))
        assertEquals(atExport.vocabulary, restored.vocabulary)
        assertEquals(atExport.verbs, restored.verbs)
        assertEquals(atExport.practiceDays, restored.practiceDays)
        assertEquals(atExport.pointsDays, restored.pointsDays)
        assertEquals(atExport.figures, restored.figures)
        assertEquals(atExport.settings, restored.settings)
        assertEquals(null, db.vocabularyDao().getById("added"))
        assertEquals(BackupInfo(listOf(BackupBook("Buch", 3, 2)), hasSettings = true, appVariant = "debug"), info)
        // The restored stages count as shown: no "new stage" moment.
        assertEquals(10, settings.lastShownFigureStage(B, 0))
        assertEquals(1, settings.lastShownFigureStage(B, 1))
        assertEquals(1, settings.restoreCount.value)
    }

    /** "Sicherung wiederherstellen" also counts as a restore when the file has no settings (the selections are in the books). */
    @Test
    fun restoreWithoutSettingsStillReloadsTheSelections() = runBlocking {
        fillEverything()
        val exported = repository.exportSync()
        repository.saveQuizFilter(QuizMode.VOCABULARY, QuizFilter())

        repository.restoreBackup(exported, settings)

        assertEquals(1, settings.restoreCount.value)
        assertEquals(QuizFilter(UnitChoice.Number(1), setOf("A"), 10, 20), repository.loadQuizFilter(QuizMode.VOCABULARY))
    }

    @Test
    fun backupWithIncludePhrasesKeepsProgressAndSettingsAndPhrasesCount() = runBlocking {
        fillEverything()
        db.vocabularyDao().insertAll(
            listOf(
                Vocabulary(
                    id = "p1", english = "Have you ever …?", germanMeanings = listOf("Hast du jemals …?"), unit = 1,
                    leitnerLevel = 4, highestLevel = 4, wordType = io.github.markushornbach.voctrainer.learning.WordType.PHRASE,
                    coreWord = "ever", updatedAt = OLD, bookId = B
                )
            )
        )
        val atExport = snapshot()
        // The settings contain the unknown key "includePhrases".
        val exported = Json.parseToJsonElement(repository.exportSync(settings.toBackup())).jsonObject
        val old = JsonObject(
            exported + ("settings" to JsonObject(exported.getValue("settings").jsonObject + ("includePhrases" to JsonPrimitive(false))))
        ).toString()

        changeEverything()
        repository.restoreBackup(old, settings)

        val restored = snapshot()
        assertEquals(atExport.vocabulary, restored.vocabulary)
        assertEquals(atExport.settings, restored.settings)
        val active = db.vocabularyDao().getAllIncludingDeleted().filterNot { it.isDeleted }.map { it.toQuizWord() }
        // bag (3), at the weekend (5), new (0), and the sure phrase (4).
        assertEquals(io.github.markushornbach.voctrainer.learning.WordCounts(sure = 2, askable = 4), io.github.markushornbach.voctrainer.learning.FigureRules.countWords(active))
        // The phrase is part of the selection "Unit 1".
        val unit1 = io.github.markushornbach.voctrainer.learning.QuizSelection.filter(active, QuizFilter(UnitChoice.Number(1)))
        assertEquals(setOf("v1", "v2", "p1"), unit1.map { it.id }.toSet())
    }

    @Test
    fun restoreWithoutSettingsKeepsTheCurrentSettings() = runBlocking {
        fillEverything()
        val exported = repository.exportSync()
        settings.setDailyGoal(2)

        val info = repository.restoreBackup(exported, settings)

        assertEquals(false, info.hasSettings)
        assertEquals(2, settings.dailyGoal.value)
    }

    @Test
    fun invalidFileChangesNothing() = runBlocking {
        fillEverything()
        val before = snapshot()
        val broken = repository.exportSync(settings.toBackup()).replace("\"id\": \"v2\"", "\"id\": \"v1\"")

        assertThrows(InvalidFileException::class.java) {
            runBlocking { repository.restoreBackup(broken, settings) }
        }

        assertEquals(before, snapshot())
        assertEquals(0, settings.restoreCount.value)
    }

    /** A backup with entries without core word and the unknown field "multipleChoiceOnly". */
    @Test
    fun restoreOfABackupWithoutCoreWordsGivesEveryEntryOneWithoutLosingProgress() = runBlocking {
        fillEverything()
        db.vocabularyDao().insertAll(
            listOf(
                Vocabulary(
                    id = "o1", english = "(to) walk", germanMeanings = listOf("gehen"), unit = 1, leitnerLevel = 4,
                    highestLevel = 5, lastReviewedAt = 940L, updatedAt = OLD
                ),
                Vocabulary(
                    id = "o2", english = "care for sb.", germanMeanings = listOf("jn. pflegen"), unit = 1,
                    leitnerLevel = 3, highestLevel = 3, choiceOnly = true, updatedAt = OLD
                )
            ).map { it.copy(bookId = B) }
        )
        // No core word for these two, "multipleChoiceOnly" for o2.
        val exported = Json.parseToJsonElement(repository.exportSync(settings.toBackup())).jsonObject
        val entries = exported.getValue("entries").jsonArray.map { entry ->
            val fields = entry.jsonObject
            if (fields.getValue("id").jsonPrimitive.content == "o2") JsonObject(fields + ("multipleChoiceOnly" to JsonPrimitive(true)))
            else fields
        }
        val old = JsonObject(exported + ("entries" to JsonArray(entries))).toString()
        changeEverything()

        val info = repository.restoreBackup(old, settings)

        assertEquals(1, info.ignoredWithoutCoreWord)
        // v1, v2, v4 and o1 stay active; o2 is ignored (v3 was ignored before).
        assertEquals(4, info.vocabulary)
        val stored = db.vocabularyDao().getAllIncludingDeleted().associateBy { it.id }
        assertEquals("walk", stored.getValue("o1").coreWord)
        assertEquals(4, stored.getValue("o1").leitnerLevel)
        assertEquals(5, stored.getValue("o1").highestLevel)
        assertEquals(940L, stored.getValue("o1").lastReviewedAt)
        assertEquals(true, stored.getValue("o2").isDeleted)
        assertEquals(3, stored.getValue("o2").leitnerLevel)
        assertEquals(false, stored.getValue("o2").choiceOnly)
        assertEquals(OLD, stored.getValue("o2").updatedAt)
        assertEquals(5, stored.getValue("v2").leitnerLevel)
        assertEquals(
            "Sicherung wiederhergestellt: Buch (4 Vokabeln, 2 Verben), Einstellungen übernommen. 1 Vokabel ohne Wort zum " +
                "Tippen wurde ignoriert. 🧪 Du findest sie in „Vokabeln editieren“.",
            io.github.markushornbach.voctrainer.ui.RestoreTexts.result(info)
        )
    }

    /** A restore replaces a newer local level and setting with the backup (no merge). */
    @Test
    fun restoreBringsTheBackupBackOverNewerLocalChanges() = runBlocking {
        fillEverything()
        val exported = repository.exportSync(settings.toBackup())
        repository.setVocabularyLevels(listOf("v1"), 0)
        settings.setDailyGoal(3)

        repository.restoreBackup(exported, settings)
        assertEquals(3, db.vocabularyDao().getById("v1")!!.leitnerLevel)
        assertEquals(4, settings.dailyGoal.value)
    }
}
