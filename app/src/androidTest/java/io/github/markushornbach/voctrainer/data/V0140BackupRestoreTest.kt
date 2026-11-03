package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.AppTheme
import io.github.markushornbach.voctrainer.settings.QuizFilterCodec
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.VerbFormMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The backup a v0.14.0 learner brings to the new app: the golden file `baseline/v0_14_0_backup.json`
 * (backup format 5, app version 0.14.0, release app) with two invented books (the example book is
 * never written into a backup), levels 0–5, ignored entries, verbs with and without past
 * participle, practice and points days of two devices, finished and growing avatars, badges, a book
 * started anew (`figureSlotStart` 1) and the settings. It was written by the code of v0.14.0 and is
 * never edited: restoring it and exporting again must give the same file.
 */
@RunWith(AndroidJUnit4::class)
class V0140BackupRestoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_v0140_backup_settings"
        const val DEVICE = "test_v0140_backup_device"
        const val FIXTURE = "v0_14_0_backup.json"
        const val MEER = "fixture-meer"
        const val WALD = "fixture-wald"
        const val DAY = 86_400_000L

        /** 2026-02-16 08:00 UTC, the first learning day of the fixture. */
        const val T0 = 1_771_228_800_000L
        val NOW: Instant = Instant.ofEpochMilli(T0 + 30 * DAY)
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        context.getSharedPreferences(DEVICE, Context.MODE_PRIVATE).edit().putString("device_id", "device-new").commit()
        AppClock.setOffsetDays(0)
        AppClock.fixedTimeForTest = NOW
        db = inMemoryDatabase(context)
        repository = VocabularyRepository(db, ExampleBook.loader(context))
        settings = AppSettings(context, SETTINGS, DEVICE)
    }

    @After
    fun tearDown() {
        AppClock.fixedTimeForTest = null
        db.closeAfterRunningQueries()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    private fun word(
        id: String, english: String, german: String, coreWord: String?, level: Int, highest: Int = level,
        unit: Int = 1, part: String? = "A", page: Int? = 10, type: WordType? = WordType.NOUN,
        reviewedDay: Int? = null, typedDay: Int? = null, ignored: Boolean = false, bookId: String = MEER
    ) = Vocabulary(
        id = id, english = english, germanMeanings = german.split("|"), unit = unit, unitTitle = "Unit $unit", part = part,
        extra = if (id.endsWith("1")) "An example sentence." else null, page = page, wordType = type,
        leitnerLevel = level, updatedAt = T0 + (reviewedDay ?: 0) * DAY + 1_000, isDeleted = ignored,
        lastReviewedAt = reviewedDay?.let { T0 + it * DAY }, coreWord = coreWord, highestLevel = highest,
        firstTypedCorrectAt = typedDay?.let { T0 + it * DAY + 500 }, bookId = bookId
    )

    /** The learning state of the fixture, as a v0.14.0 learner has it. */
    private suspend fun fillLikeV0140() {
        repository.ensureExampleBook()
        db.bookDao().insertAll(
            listOf(
                Book(MEER, "Meerbuch", "en", quizFilter = QuizFilterCodec.encode(QuizFilter(unit = UnitChoice.Number(2)))),
                Book(WALD, "Waldbuch", "en", verbQuizFilter = QuizFilterCodec.encode(QuizFilter()), figureSlotStart = 1)
            )
        )
        db.bookDao().setActive(MEER)
        db.vocabularyDao().insertAll(
            listOf(
                word("m1", "fish", "Fisch", "fish", 0),
                word("m2", "boat", "Boot", "boat", 1, reviewedDay = 3),
                word("m3", "(to) swim", "schwimmen", "swim", 2, highest = 3, type = WordType.VERB, reviewedDay = 4),
                word("m4", "deep", "tief", "deep", 3, type = WordType.OTHER, reviewedDay = 4, page = 11),
                word("m5", "island", "Insel", "island", 4, unit = 2, part = "B", page = 20, reviewedDay = 5, typedDay = 5),
                word("m6", "by the sea", "am Meer", "sea", 5, unit = 2, part = "B", page = 20, type = WordType.PHRASE, reviewedDay = 6, typedDay = 2),
                word("m7", "calm", "ruhig|still", "calm", 5, unit = 2, part = null, page = null, type = null, reviewedDay = 6, typedDay = 3),
                word("m8", "salty", "salzig", null, 2, highest = 4, unit = 2, part = "B", page = 21, reviewedDay = 2, ignored = true),
                word("w1", "tree", "Baum", "tree", 1, reviewedDay = 9, bookId = WALD),
                word("w2", "leaf", "Blatt", "leaf", 0, highest = 4, bookId = WALD),
                word("w3", "(to) climb", "klettern", "climb", 2, highest = 5, type = WordType.VERB, reviewedDay = 9, bookId = WALD)
            )
        )
        db.irregularVerbDao().insertAll(
            listOf(
                IrregularVerb("i1", "(to) swim", "swam", "swum", listOf("schwimmen"), 1, "A", 10, false, 3, T0 + 4 * DAY, false, T0 + 4 * DAY, MEER),
                IrregularVerb("i2", "(to) dig", "dug", null, listOf("graben"), 1, "B", 11, true, 1, T0 + 3 * DAY, false, T0 + 3 * DAY, MEER),
                IrregularVerb("i3", "(to) sink", "sank", "sunk", listOf("sinken"), 2, null, null, false, 5, T0 + 6 * DAY, false, T0 + 6 * DAY, MEER),
                IrregularVerb("i4", "(to) hide", "hid", "hidden", listOf("sich verstecken", "verstecken"), 1, "A", 10, false, 0, T0, true, null, MEER),
                IrregularVerb("i5", "(to) grow", "grew", "grown", listOf("wachsen"), 1, "A", 10, false, 2, T0 + 9 * DAY, false, T0 + 9 * DAY, WALD)
            )
        )
        fun date(day: Int) = java.time.LocalDate.of(2026, 2, 16).plusDays(day.toLong()).toString()
        db.practiceDayDao().upsertAll(
            listOf(
                PracticeDay(date(2), "device-a", 2, 2, T0 + 2 * DAY + 10, MEER),
                PracticeDay(date(3), "device-a", 3, 2, T0 + 3 * DAY + 10, MEER),
                PracticeDay(date(4), "device-a", 2, 2, T0 + 4 * DAY + 10, MEER),
                PracticeDay(date(6), "device-b", 1, 3, T0 + 6 * DAY + 10, MEER),
                PracticeDay(date(8), "device-a", 0, 2, T0 + 9 * DAY + 10, WALD),
                PracticeDay(date(9), "device-a", 2, 2, T0 + 9 * DAY + 10, WALD)
            )
        )
        // Meerbuch: 80 rise + 23 bonus + 5 start points = 108; the growing monster started at 88 (stage 2).
        db.pointsDayDao().upsertAll(
            listOf(
                PointsDay(date(2), "device-a", 40, 11, 5, T0 + 2 * DAY + 20, MEER),
                PointsDay(date(3), "device-a", 30, 12, 0, T0 + 3 * DAY + 20, MEER),
                PointsDay(date(6), "device-b", 10, 0, 0, T0 + 6 * DAY + 20, MEER),
                PointsDay(date(8), "device-a", 0, 0, 0, T0 + 9 * DAY + 20, WALD),
                PointsDay(date(9), "device-a", 5, 0, 0, T0 + 9 * DAY + 30, WALD)
            )
        )
        db.figureDao().upsertAll(
            listOf(
                Figure(0, "dragon", 0, 10, T0, T0 + 5 * DAY, T0 + 5 * DAY, MEER),
                Figure(1, "monster", 88, 2, T0 + 5 * DAY + 100, null, T0 + 6 * DAY, MEER),
                Figure(0, "fighter", 0, 10, T0 + DAY, T0 + 7 * DAY, T0 + 8 * DAY, WALD),
                Figure(1, "dragon", 0, 1, T0 + 8 * DAY + 100, null, T0 + 9 * DAY, WALD)
            )
        )
        db.badgeDao().upsertAll(
            listOf(Badge("streak", 1, T0 + 4 * DAY + 50, MEER), Badge("sure_words", 1, T0 + 6 * DAY + 50, WALD))
        )
        settings.setTheme(AppTheme.GAMING_DARK)
        settings.setVibration(true)
        settings.setAutoSpeak(false)
        settings.setEditButtonInQuiz(true)
        settings.setQuizMode(QuizMode.VOCABULARY)
        settings.setVerbFormMode(VerbFormMode.THREE)
        settings.setDailyGoal(4)
        settings.setReminderEnabled(true)
        settings.setReminderMinutes(7 * 60 + 15)
        settings.tipUnderstood(Tip.SELECTION)
        settings.tipUnderstood(Tip.START_BUTTON)
        settings.tipUnderstood(Tip.TYPING)
    }

    /** The fixture is what the code writes for this learning state (as the release app 0.14.0). */
    @Test
    fun theCodeWritesTheFixture() = runBlocking {
        fillLikeV0140()
        val written = BaselineFiles.parse(repository.exportSync(settings.toBackup())).let {
            kotlinx.serialization.json.JsonObject(
                it + mapOf("appVersion" to JsonPrimitive("0.14.0"), "appVariant" to JsonPrimitive(VocTrainerJson.APP_VARIANT_RELEASE))
            )
        }
        BaselineFiles.writeActual(FIXTURE, written.toString())
        val fixture = BaselineFiles.asset(FIXTURE)
        assertNotNull("fixture $FIXTURE missing; the actual file was written to files/baseline", fixture)
        BaselineFiles.assertSame(BaselineFiles.parse(fixture!!), written)
    }

    /** Restore the v0.14.0 backup into a fresh app, export again: every value comes back unchanged. */
    @Test
    fun theV0140BackupSurvivesARestore() = runBlocking {
        val fixture = BaselineFiles.parse(BaselineFiles.asset(FIXTURE)!!)
        assertEquals(5, fixture["version"]!!.jsonPrimitive.content.toInt())
        assertEquals("0.14.0", fixture["appVersion"]!!.jsonPrimitive.content)
        assertEquals(VocTrainerJson.APP_VARIANT_RELEASE, fixture["appVariant"]!!.jsonPrimitive.content)

        repository.ensureExampleBook()
        repository.restoreBackup(fixture.toString(), settings)
        val again = BaselineFiles.parse(repository.exportSync(settings.toBackup()))
        BaselineFiles.writeActual("restored_$FIXTURE", again.toString())

        // The app variant is the one of the app that exported again.
        assertEquals(VocTrainerJson.appVariant(BuildConfig.DEBUG), again["appVariant"]!!.jsonPrimitive.content)
        BaselineFiles.assertSame(
            BaselineFiles.without(fixture, "appVersion", "appVariant"),
            BaselineFiles.without(again, "appVersion", "appVariant")
        )
        // The example book is back, inactive; the learner's active book stays active.
        assertEquals(MEER, db.bookDao().getActive()!!.id)
        assertNotNull(db.bookDao().getById(ExampleBook.ID))
    }
}
