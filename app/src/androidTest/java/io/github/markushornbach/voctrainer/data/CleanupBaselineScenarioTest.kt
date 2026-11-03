package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.Due
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.KeptAnswer
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.Streak
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.AppTheme
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.VerbFormMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A fixed learning scenario over many simulated days, driven through the repository and the
 * learning rules like the quiz does (not through the UI), with a fixed clock and device id: example
 * book, an invented second book, a book switch, rounds with right, wrong, „Fast richtig“, „Auch
 * richtig“, typed and „Mit Vorschlägen“ answers, ignored entries, verbs with two and three forms,
 * daily goal, streak, badges, a finished avatar, „Buch neu beginnen“, a new avatar, changed settings
 * and a backup at the end. The backup must equal the golden file `baseline/scenario_backup.json`,
 * and the backup just before „Buch neu beginnen“ `baseline/scenario_before_restart.json` (only
 * `appVersion` is ignored, and the generated entry ids are matched by book and text).
 */
@RunWith(AndroidJUnit4::class)
class CleanupBaselineScenarioTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.systemDefault()
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository
    private lateinit var settings: AppSettings
    private var tick = 0L
    private var dayStart: Instant = START
    private var today: LocalDate = LocalDate.MIN

    private companion object {
        const val SETTINGS = "test_baseline_scenario_settings"
        const val DEVICE = "test_baseline_scenario_device"
        const val DEVICE_ID = "baseline-device"
        const val BOOK = "baseline-ozean"
        const val GOLDEN = "scenario_backup.json"
        const val GOLDEN_BEFORE_RESTART = "scenario_before_restart.json"
        const val PHASE_2_DAYS = 10

        /** Day 0 of the scenario, a Monday at noon UTC (the same calendar day in every usual time zone). */
        val START: Instant = LocalDate.of(2026, 3, 2).atTime(12, 0).toInstant(ZoneOffset.UTC)
        const val MAX_DAYS = 120
        const val SECONDS_PER_DAY = 86_400L
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        context.getSharedPreferences(DEVICE, Context.MODE_PRIVATE).edit().putString("device_id", DEVICE_ID).commit()
        AppClock.setOffsetDays(0)
        db = inMemoryDatabase(context)
        repository = VocabularyRepository(db, ExampleBook.loader(context))
        settings = AppSettings(context, SETTINGS, DEVICE)
        setDay(0)
    }

    @After
    fun tearDown() {
        AppClock.fixedTimeForTest = null
        AppClock.setOffsetDays(0)
        db.closeAfterRunningQueries()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    /** Moves the clock to [day] of the scenario; every later action is one second later. */
    private fun setDay(day: Int) {
        tick = 0
        dayStart = START.plusSeconds(day * SECONDS_PER_DAY)
        today = dayStart.atZone(zone).toLocalDate()
        AppClock.fixedTimeForTest = dayStart
    }

    private fun step() {
        tick++
        AppClock.fixedTimeForTest = dayStart.plusSeconds(tick)
    }

    private suspend fun activeWords() = db.vocabularyDao().getAllIncludingDeleted().filterNot { it.isDeleted }

    private suspend fun idOf(english: String) = db.vocabularyDao().getAllIncludingDeleted().first { it.english == english }.id

    /** The answer the learner gives on [day] of a phase to [word] at its current level. */
    private fun answer(day: Int, word: Vocabulary): Triple<Boolean, AnswerForm, KeptAnswer?> {
        val typing = word.toQuizWord().isTypeable && word.leitnerLevel >= 3
        val form = if (typing) AnswerForm.TYPED else AnswerForm.MULTIPLE_CHOICE
        return when {
            word.english == "storm" && day in setOf(3, 12) -> Triple(false, form, null)
            word.english == "wave" && day == 9 -> Triple(false, form, null)
            word.english == "seagull" && day == 17 -> Triple(false, form, null)
            typing && word.english == "shell" && day in setOf(4, 5) -> Triple(true, AnswerForm.CHOICE_INSTEAD_OF_TYPING, null)
            typing && word.english == "lighthouse" && day in setOf(6, 7) -> Triple(false, form, KeptAnswer.ALMOST_CORRECT)
            typing && word.english == "silent" && day in setOf(6, 7) -> Triple(false, form, KeptAnswer.ALSO_CORRECT)
            else -> Triple(true, form, null)
        }
    }

    /**
     * One vocabulary round of the active book: the due words (at most 10), or, if none is due, the
     * four lowest words asked early; then the round counts like in the quiz (goal, bonus, figure, badges).
     */
    private suspend fun vocabularyRound(day: Int) {
        val words = activeWords()
        val due = words.filter { Due.isDue(it.toQuizWord(), today, zone) }.take(10)
        val early = due.isEmpty()
        val asked = if (early) words.sortedBy { it.leitnerLevel }.take(4) else due
        for (word in asked) {
            step()
            val (correct, form, kept) = answer(day, word)
            repository.recordFirstAttempt(word.id, correct, early, today.toString(), DEVICE_ID, form, kept, zone)
        }
        finishRound()
    }

    /** Like the quiz at the end of a vocabulary round. */
    private suspend fun finishRound() {
        step()
        val goal = settings.dailyGoal.value
        suspend fun streak() = Streak.compute(repository.allPracticeDays().map { it.toRecord() }, today, DEVICE_ID, goal)
        val before = streak()
        repository.recordRoundCompleted(today.toString(), DEVICE_ID, goal)
        val after = streak()
        if (!before.todayReached && after.todayReached) {
            repository.recordDailyBonus(today.toString(), DEVICE_ID, Points.dailyBonus(after.current))
        }
        repository.updateFigureStage()
        repository.checkBadges(settings, longestStreak = after.longest)
    }

    /** One verb round of the due verbs; wrong only for „(to) sink“ on day 5. No rewards. */
    private suspend fun verbRound(day: Int) {
        val verbs = db.irregularVerbDao().getAllIncludingDeleted().filterNot { it.isDeleted }
            .filter { Due.isDue(it.leitnerLevel, it.lastReviewedAt, today, zone) }
            .filter { settings.verbFormMode.value == VerbFormMode.TWO || it.pastParticiple != null }
        for (verb in verbs) {
            step()
            repository.recordVerbFirstAttempt(verb.id, !(verb.infinitive == "(to) sink" && day == 5), false, today.toString(), DEVICE_ID, zone)
        }
    }

    /** Two vocabulary rounds and one verb round on [day]; the answers follow [phaseDay]. */
    private suspend fun playDay(day: Int, phaseDay: Int) {
        setDay(day)
        vocabularyRound(phaseDay)
        vocabularyRound(phaseDay)
        verbRound(phaseDay)
    }

    private suspend fun figures() = db.figureDao().getAll()

    @Test
    fun theScenarioWritesTheGoldenBackup() = runBlocking {
        // Day 0: the example book, one round in it, then the invented book and the switch to it.
        step()
        repository.ensureExampleBook()
        settings.finishFirstStart()
        // Chosen explicitly, so the scenario does not depend on the default of a new installation.
        settings.setAutoSpeak(true)
        repository.chooseFigure(FigureKind.DRAGON)
        settings.tipUnderstood(Tip.SELECTION)
        vocabularyRound(0)
        step()
        val import = repository.importVocabulary(BaselineFiles.asset("ozean_import.json")!!)
        assertEquals(BOOK, import.bookId)
        step()
        repository.setActiveBook(BOOK)
        step()
        repository.saveQuizFilter(QuizMode.VOCABULARY, QuizFilter(unit = UnitChoice.Number(1)))
        step()
        repository.chooseFigure(FigureKind.MONSTER)

        // Phase 1: until the first avatar of the book is finished.
        var day = phase(firstDay = 1) { figures().any { it.finishedAt != null } }
        assertTrue(day < MAX_DAYS)
        step()
        val beforeRestart = repository.exportSync(settings.toBackup())
        BaselineFiles.writeActual(GOLDEN_BEFORE_RESTART, beforeRestart)

        // „Buch neu beginnen“ after the finished avatar, a new avatar, and phase 2 over 10 days.
        setDay(day)
        step()
        assertEquals("Ozeanbuch", repository.restartBook(settings))
        step()
        repository.chooseFigure(FigureKind.FIGHTER)
        val restartDay = day
        day = phase(firstDay = day, secondPhase = true) { it - restartDay >= PHASE_2_DAYS }

        // Settings that are part of the backup.
        step()
        settings.setTheme(AppTheme.SUNNY)
        settings.setVibration(true)
        settings.setAutoSpeak(true)
        settings.setEditButtonInQuiz(true)
        settings.setQuizMode(QuizMode.VERBS)
        settings.setVerbFormMode(VerbFormMode.THREE)
        settings.setDailyGoal(3)
        settings.setReminderEnabled(true)
        settings.setReminderMinutes(18 * 60 + 30)
        settings.tipUnderstood(Tip.START_BUTTON)
        settings.tipUnderstood(Tip.LEVELS)

        step()
        val backup = repository.exportSync(settings.toBackup())
        BaselineFiles.writeActual(GOLDEN, backup)

        // The scenario reached what it is meant to cover.
        val all = figures()
        assertTrue(all.any { it.finishedAt != null } && all.any { it.finishedAt == null && it.stage > 0 })
        assertTrue(db.badgeDao().getAll().isNotEmpty())
        assertTrue(db.bookDao().getById(BOOK)!!.figureSlotStart > 0)

        assertGolden(GOLDEN_BEFORE_RESTART, beforeRestart)
        assertGolden(GOLDEN, backup)
    }

    private fun assertGolden(name: String, actual: String) {
        val golden = BaselineFiles.asset(name)
        assertNotNull("golden file $name missing; the actual file was logged (tag Baseline)", golden)
        fun normalized(text: String) = BaselineFiles.withStableIds(BaselineFiles.without(BaselineFiles.parse(text), "appVersion"))
        BaselineFiles.assertSame(normalized(golden!!), normalized(actual))
    }

    /**
     * Plays one day after the other from [firstDay] until [done] (checked before each day, gets the
     * day) holds; returns the first day not played. Day 8 of a phase is skipped (the streak breaks).
     * Phase 1 ignores „rope“ on its day 2, switches to three verb forms on day 10 and ignores „wet“
     * from day 11 to 13; phase 2 ignores „harbour“ on its day 2 for good and goes back to two verb
     * forms on day 10.
     */
    private suspend fun phase(firstDay: Int, secondPhase: Boolean = false, done: suspend (Int) -> Boolean): Int {
        var day = firstDay
        while (!done(day)) {
            assertTrue("phase not done after $MAX_DAYS days", day < MAX_DAYS)
            val phaseDay = day - firstDay + 1
            if (phaseDay != 8) {
                setDay(day)
                step()
                when {
                    !secondPhase && phaseDay == 2 -> repository.setIgnored(idOf("rope"), true)
                    !secondPhase && phaseDay == 10 -> settings.setVerbFormMode(VerbFormMode.THREE)
                    !secondPhase && phaseDay == 11 -> repository.setIgnored(idOf("wet"), true)
                    !secondPhase && phaseDay == 13 -> repository.setIgnored(idOf("wet"), false)
                    secondPhase && phaseDay == 2 -> repository.setIgnored(idOf("harbour"), true)
                    secondPhase && phaseDay == 10 -> settings.setVerbFormMode(VerbFormMode.TWO)
                }
                playDay(day, phaseDay)
            }
            day++
        }
        return day
    }
}
