package io.github.markushornbach.voctrainer.data

import androidx.room.withTransaction
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.BadgeKey
import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.learning.SelfTyped
import io.github.markushornbach.voctrainer.learning.Streak
import io.github.markushornbach.voctrainer.learning.Due
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.QuestionDirection
import io.github.markushornbach.voctrainer.learning.KeptAnswer
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.LevelOutcome
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizFilterCodec
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.SettingsBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** A book of a backup file: its title and its active (not ignored) vocabulary and verbs. */
data class BackupBook(val title: String, val vocabulary: Int, val verbs: Int)

/**
 * What a backup file contains, shown before "Sicherung wiederherstellen" replaces anything: the
 * real [books] in file order (never the example book, see [ExampleBook]); [appVariant] see
 * [VocTrainerJson.appVariant]. After the restore, the vocabulary counts no longer include the
 * [ignoredWithoutCoreWord] entries.
 */
data class BackupInfo(
    val books: List<BackupBook>,
    val hasSettings: Boolean,
    val appVariant: String?,
    val ignoredWithoutCoreWord: Int = 0
) {
    val vocabulary: Int get() = books.sumOf { it.vocabulary }
    val verbs: Int get() = books.sumOf { it.verbs }
}

private fun SyncData.info() = BackupInfo(
    books.map { book ->
        BackupBook(
            book.title,
            vocabulary.count { it.bookId == book.id && !it.isDeleted },
            irregularVerbs.count { it.bookId == book.id && !it.isDeleted }
        )
    },
    settings != null, appVariant
)

/**
 * The outcome of "Vokabeln importieren": the [counts] and the book the file went into.
 * [isNewBook]: the book was created by this import; [isActive]: it is the active book now (a new
 * book is active only if there was no other book; otherwise the learner is asked).
 */
data class BookImport(
    val counts: VocabularyImportResult,
    val bookId: String,
    val bookTitle: String,
    val isNewBook: Boolean,
    val isActive: Boolean
)

/** A row of the book list in "Vokabeln verwalten": active words and how many of them are learned ("gelernt"). */
data class BookSummary(val book: Book, val words: Int, val learnedWords: Int, val label: String = book.title)

/** Suffix of the example book in the book list of the test app (debug build). */
const val EXAMPLE_BOOK_TEST_SUFFIX = " (Test)"

/**
 * Which books the list shows and under which name. The example book is listed only while it is
 * active; with [exampleBookAlways] (test app) it is always listed, as the last entry, with
 * [EXAMPLE_BOOK_TEST_SUFFIX].
 */
fun listedBooks(books: List<Book>, exampleBookAlways: Boolean): List<Pair<Book, String>> {
    if (!exampleBookAlways) return books.filter { it.id != ExampleBook.ID || it.isActive }.map { it to it.title }
    val (example, own) = books.partition { it.id == ExampleBook.ID }
    return own.map { it to it.title } + example.map { it to it.title + EXAMPLE_BOOK_TEST_SUFFIX }
}

/** A figure that reached a new stage; [finished] at the last stage. */
data class FigureGrowth(val kind: FigureKind, val stage: Int, val finished: Boolean)

/** Outcome of a recorded first attempt: rise points and whether the level went up. */
data class FirstAttemptResult(val points: Int, val rose: Boolean, val outcome: LevelOutcome? = null) {
    companion object {
        val NONE = FirstAttemptResult(0, false)
    }
}

/** The saved selection of [mode] in this book; null = none saved (the default selection applies). */
fun Book.quizFilter(mode: QuizMode): QuizFilter? = when (mode) {
    QuizMode.VOCABULARY -> quizFilter
    QuizMode.VERBS -> verbQuizFilter
}?.let(QuizFilterCodec::decode)

/**
 * Everything the learner sees, counts and changes works on the active book (see [Book]); only
 * the book list, the import into a named book, the backup and the restore see all books.
 * [exampleBook] reads the import file of the example book (see [ensureExampleBook]); without it
 * (most tests) there is no example book. [exampleBookAlwaysListed] (test app) puts it always in
 * the book list, see [listedBooks].
 */
class VocabularyRepository(
    private val db: VocTrainerDatabase,
    private val exampleBook: (() -> String)? = null,
    private val exampleBookAlwaysListed: Boolean = false
) {
    private val bookDao = db.bookDao()
    private val vocabularyDao = db.vocabularyDao()
    private val verbDao = db.irregularVerbDao()
    private val practiceDayDao = db.practiceDayDao()
    private val pointsDayDao = db.pointsDayDao()
    private val figureDao = db.figureDao()
    private val badgeDao = db.badgeDao()

    /** The active book; null only while no book exists. */
    val activeBook: Flow<Book?> = bookDao.observeActive()

    suspend fun activeBookNow(): Book? = bookDao.getActive()

    /**
     * The books with their counts, in the order they were added (see [listedBooks] for the example
     * book: listed only while active, in the test app always).
     */
    val bookSummaries: Flow<List<BookSummary>> =
        combine(bookDao.observeAll(), vocabularyDao.observeAllBooksActive()) { books, words ->
            val byBook = words.groupBy { it.bookId }
            listedBooks(books, exampleBookAlwaysListed).map { (book, label) ->
                val list = byBook[book.id].orEmpty().map { it.toQuizWord() }
                BookSummary(book, list.size, FigureRules.countWords(list).sure, label)
            }
        }

    /** True once the database holds a book other than the example book. */
    val hasOwnBook: Flow<Boolean> = bookDao.observeAll().map { books -> books.any { it.id != ExampleBook.ID } }

    /** Makes [id] the active book; changes no data, only what is shown. Unknown ids are ignored. */
    suspend fun setActiveBook(id: String) {
        db.withTransaction { if (bookDao.getById(id) != null) bookDao.setActive(id) }
    }

    /** Saves the selection of [mode] in the active book ("Auswahl übernehmen"). */
    suspend fun saveQuizFilter(mode: QuizMode, filter: QuizFilter) {
        db.withTransaction {
            val book = bookDao.getActive() ?: return@withTransaction
            val encoded = QuizFilterCodec.encode(filter)
            bookDao.update(
                when (mode) {
                    QuizMode.VOCABULARY -> book.copy(quizFilter = encoded)
                    QuizMode.VERBS -> book.copy(verbQuizFilter = encoded)
                }
            )
        }
    }

    /** The saved selection of [mode] in the active book; null = none (or no book). */
    suspend fun loadQuizFilter(mode: QuizMode): QuizFilter? = bookDao.getActive()?.quizFilter(mode)

    val activeVocabularyCount: Flow<Int> = vocabularyDao.countActive()

    /** Including ignored entries, in insertion order. */
    val allVocabularyIncludingIgnored: Flow<List<Vocabulary>> =
        vocabularyDao.observeAllIncludingDeletedInInsertionOrder()

    val vocabularyInInsertionOrder: Flow<List<Vocabulary>> = vocabularyDao.getAllInInsertionOrder()

    val verbsInInsertionOrder: Flow<List<IrregularVerb>> = verbDao.getAllInInsertionOrder()

    val practiceDays: Flow<List<PracticeDay>> = practiceDayDao.observeAll()

    suspend fun allPracticeDays(): List<PracticeDay> = practiceDayDao.getAll()

    /** Total points of the active book over all devices (see [Points.total]). */
    val totalPoints: Flow<Int> = pointsDayDao.observeAll().map { rows -> Points.total(rows.map { it.toRecord() }) }

    suspend fun totalPointsNow(): Int = Points.total(pointsDayDao.getAll().map { it.toRecord() })

    val figures: Flow<List<Figure>> = figureDao.observeAll()

    /** [Book.figureSlotStart] of the active book; 0 without a book. */
    val figureSlotStart: Flow<Int> = activeBook.map { it?.figureSlotStart ?: 0 }

    /** The active book has something "Buch neu beginnen" would reset (see [BookRestart.hasSomethingToReset]). */
    val canRestartBook: Flow<Boolean> = combine(
        combine(vocabularyDao.observeAllIncludingDeletedInInsertionOrder(), verbDao.getAllInInsertionOrder(), ::Pair),
        combine(pointsDayDao.observeAll(), practiceDayDao.observeAll(), ::Pair),
        badgeDao.observeAll(), figureDao.observeAll(), activeBook
    ) { (vocabulary, verbs), (points, practice), badges, figures, book ->
        book != null && BookRestart.hasSomethingToReset(vocabulary, verbs, points, practice, badges, figures, book.figureSlotStart)
    }

    /**
     * Starts a figure of [kind] in the next slot (see [startNext]) and computes its stage at once,
     * since existing points can already give stage 1–3. Does nothing while a figure is growing or a new avatar is paused (not enough new words).
     */
    suspend fun chooseFigure(kind: FigureKind) {
        db.withTransaction {
            val book = bookDao.getActive() ?: return@withTransaction
            val askable = activeBookWords(book.id).size
            val next = figureDao.getAll().startNext(kind, totalPointsNow(), AppClock.currentTimeMillis(), askable, book.figureSlotStart)
            if (next != null) {
                figureDao.upsert(next.copy(bookId = book.id))
                updateFigureStage()
            }
        }
    }

    /**
     * "Buch neu beginnen": starts the active book from scratch in one transaction (see [BookRestart]):
     * all words and verbs back to new, points 0 ([PointsDayReset]), practice days without rounds
     * (streak 0), badges deleted, and the figures so far moved to an earlier start
     * ([Book.figureSlotStart]), so the learner chooses avatar 1 again; finished figures stay in the
     * collection. The vocabulary, the selections, other books and all settings stay. Afterwards the
     * stage and badge checks run silently. Returns the title of the book, or null without one.
     */
    suspend fun restartBook(settings: AppSettings): String? {
        val title = db.withTransaction {
            val book = bookDao.getActive() ?: return@withTransaction null
            val now = AppClock.currentTimeMillis()
            vocabularyDao.updateAll(BookRestart.vocabulary(vocabularyDao.getAllIncludingDeleted(), now))
            verbDao.updateAll(BookRestart.verbs(verbDao.getAllIncludingDeleted(), now))
            pointsDayDao.upsertAll(PointsDayReset.cleared(pointsDayDao.getAll(), now))
            practiceDayDao.upsertAll(BookRestart.practiceDays(practiceDayDao.getAll(), now))
            badgeDao.deleteAllOfActiveBook()
            bookDao.update(book.copy(figureSlotStart = BookRestart.figureSlotStart(figureDao.getAll())))
            book.title
        } ?: return null
        updateFigureStage()
        checkBadges(settings)
        return title
    }

    /**
     * Raises the stage of the current figure of the active book if the points since its start and
     * the sure words of the whole book allow it (see [FigureRules]); never lowers it. Returns the
     * growth, or null.
     */
    suspend fun updateFigureStage(): FigureGrowth? = db.withTransaction {
        bookDao.getActive()?.let { updateFigureStageOf(it.id) }
    }

    /** The non-ignored vocabulary entries of [bookId], as the figure rules count them. */
    private suspend fun activeBookWords(bookId: String): List<QuizWord> =
        vocabularyDao.getAllOfBookIncludingDeleted(bookId).filterNot { it.isDeleted }.map { it.toQuizWord() }

    /** [updateFigureStage] for any book. The quiz selection plays no part: all words of the book count. */
    private suspend fun updateFigureStageOf(bookId: String): FigureGrowth? = db.withTransaction {
        val figures = figureDao.getAllOfBook(bookId)
        val slotStart = bookDao.getById(bookId)?.figureSlotStart ?: 0
        val figure = figures.current(slotStart) ?: return@withTransaction null
        val kind = figure.figureKind ?: return@withTransaction null
        val words = activeBookWords(bookId)
        val total = Points.total(pointsDayDao.getAllOfBook(bookId).map { it.toRecord() })
        val grown = figure.grown(
            FigureRules.pointsSinceStart(total, figure.pointsAtStart),
            FigureRules.countWords(words),
            figures.avatarPlan(words.size, slotStart).number,
            AppClock.currentTimeMillis()
        ) ?: return@withTransaction null
        figureDao.upsert(grown)
        FigureGrowth(kind, grown.stage, grown.isFinished)
    }

    val badges: Flow<List<Badge>> = badgeDao.observeAll()

    /** Longest streak of the active book over all devices (see [Streak.compute]), with this device's [dailyGoal]. */
    suspend fun longestStreak(deviceId: String, dailyGoal: Int): Int =
        Streak.compute(practiceDayDao.getAll().map { it.toRecord() }, AppClock.today(), deviceId, dailyGoal).longest

    /**
     * Awards every tier that the current values justify and that is not earned yet (see
     * [BadgeRules.newlyReached]), in one transaction; returns the newly earned tiers. Sure words
     * come from the whole active book, [longestStreak] from the caller. Earned badges are never
     * removed or overwritten.
     */
    suspend fun awardBadges(longestStreak: Int): List<BadgeKey> = db.withTransaction {
        val book = bookDao.getActive() ?: return@withTransaction emptyList()
        val values = badgeValues(vocabularyDao.getAllIncludingDeleted(), longestStreak)
        val newly = BadgeRules.newlyReached(values, badgeDao.getAll().keys())
        val now = AppClock.currentTimeMillis()
        badgeDao.insertNew(newly.map { Badge(it.series.key, it.tier, now, book.id) })
        newly
    }

    /**
     * Checks the badges with the learner's settings ([longestStreak] if the caller knows it); also
     * awards earlier progress retroactively, without any notice. Serialized over all repository
     * instances, so two checks at the start never award the same tier twice.
     */
    suspend fun checkBadges(settings: AppSettings, longestStreak: Int? = null): List<BadgeKey> =
        badgeLock.withLock {
            awardBadges(longestStreak ?: longestStreak(settings.deviceId, settings.dailyGoal.value))
        }

    suspend fun badgeCount(): Int = badgeDao.getAll().keys().size

    /** Test app only ("Abzeichen zurücksetzen"): deletes the badges of the active book; returns how many there were. */
    suspend fun deleteAllBadgesForTest(): Int = if (BuildConfig.DEBUG) badgeDao.deleteAllOfActiveBook() else 0

    /** Test app only: adds rise points for [date] on this device in the active book. */
    suspend fun addTestPoints(date: String, deviceId: String, points: Int) {
        db.withTransaction {
            val book = bookDao.getActive() ?: return@withTransaction
            addRisePoints(book.id, date, deviceId, points, AppClock.currentTimeMillis())
        }
    }

    /**
     * Test app only ("Punkte setzen"): makes the total points of the active book equal [target]
     * by changing the rise points of [date] on this device (see [Points.risePointsForTotal]); may
     * store negative rise points. The caller recomputes the figure stage afterwards.
     */
    suspend fun setTotalPointsForTest(date: String, deviceId: String, target: Int) {
        if (!BuildConfig.DEBUG) return
        db.withTransaction {
            val book = bookDao.getActive() ?: return@withTransaction
            val rows = pointsDayDao.getAll()
            val rise = Points.risePointsForTotal(rows.map { it.toRecord() }, date, deviceId, target)
            val now = AppClock.currentTimeMillis()
            val row = pointsDayDao.get(book.id, date, deviceId) ?: PointsDay(date, deviceId, updatedAt = now, bookId = book.id)
            pointsDayDao.upsert(row.copy(risePoints = rise, updatedAt = now))
        }
    }

    /**
     * Test app only ("Stufe setzen"): puts the current figure on [stage], ignoring the
     * requirements (see [withStage]). Returns the figure, or null if none is being grown.
     */
    suspend fun setFigureStageForTest(stage: Int): Figure? {
        if (!BuildConfig.DEBUG) return null
        return db.withTransaction {
            val slotStart = bookDao.getActive()?.figureSlotStart ?: 0
            val figure = figureDao.getAll().current(slotStart) ?: return@withTransaction null
            figure.withStage(stage, AppClock.currentTimeMillis()).also { figureDao.upsert(it) }
        }
    }

    /** Test app only ("Avatar zurücksetzen"): deletes the figures of the active book; returns how many there were. */
    suspend fun deleteAllFiguresForTest(): Int = if (BuildConfig.DEBUG) figureDao.deleteAllOfActiveBook() else 0

    /**
     * Test app only ("Alle Vokabeln entfernen"): physically deletes all vocabulary and irregular
     * verbs of the active book (not the book, points, avatars or badges), so a fresh import can be
     * tried. Returns how many entries were removed.
     */
    suspend fun deleteAllVocabularyForTest(): Int {
        if (!BuildConfig.DEBUG) return 0
        return db.withTransaction {
            val count = vocabularyDao.getAllIncludingDeleted().size + verbDao.getAllIncludingDeleted().size
            vocabularyDao.deleteAllOfActiveBook()
            verbDao.deleteAllOfActiveBook()
            count
        }
    }

    /**
     * Test app only ("Zweites Testbuch anlegen"): creates the book [TEST_BOOK_ID] with a few
     * invented words and verbs, inactive (active only if there is no active book). Returns false
     * and changes nothing if the book exists.
     */
    suspend fun createTestBookForTest(): Boolean {
        if (!BuildConfig.DEBUG) return false
        return db.withTransaction {
            if (bookDao.getById(TEST_BOOK_ID) != null) return@withTransaction false
            val noActive = bookDao.getActive() == null
            bookDao.insert(Book(TEST_BOOK_ID, TEST_BOOK_TITLE, Book.DEFAULT_LANGUAGE, isActive = noActive))
            val now = AppClock.currentTimeMillis()
            vocabularyDao.insertAll(TestBook.vocabulary(TEST_BOOK_ID, now))
            verbDao.insertAll(TestBook.verbs(TEST_BOOK_ID, now))
            true
        }
    }

    /** Test app only: sets the given irregular verbs to [level] (see [LevelReset.verbsTo]). */
    suspend fun setVerbLevels(ids: Collection<String>, level: Int): Int {
        if (!BuildConfig.DEBUG) return 0
        return db.withTransaction {
            val changed = LevelReset.verbsTo(verbDao.getAllIncludingDeleted(), ids.toSet(), level, AppClock.currentTimeMillis())
            verbDao.updateAll(changed)
            changed.size
        }
    }

    /** Test app only ("Auswahl jetzt fällig machen"): clears the review dates of the given vocabulary. */
    suspend fun makeVocabularyDue(ids: Collection<String>) {
        if (!BuildConfig.DEBUG) return
        db.withTransaction {
            vocabularyDao.updateAll(
                LevelReset.vocabularyDueNow(vocabularyDao.getAllIncludingDeleted(), ids.toSet(), AppClock.currentTimeMillis())
            )
        }
    }

    /** Test app only: like [makeVocabularyDue] for irregular verbs. */
    suspend fun makeVerbsDue(ids: Collection<String>) {
        if (!BuildConfig.DEBUG) return
        db.withTransaction {
            verbDao.updateAll(LevelReset.verbsDueNow(verbDao.getAllIncludingDeleted(), ids.toSet(), AppClock.currentTimeMillis()))
        }
    }

    /** Levels of all active vocabulary entries and irregular verbs of the active book; empty = no progress yet. */
    suspend fun activeLevels(): List<Int> = db.withTransaction {
        vocabularyDao.getAllIncludingDeleted().filterNot { it.isDeleted }.map { it.leitnerLevel } +
            verbDao.getAllIncludingDeleted().filterNot { it.isDeleted }.map { it.leitnerLevel }
    }

    /**
     * Stores the daily bonus for [date] once in the active book; returns the stored bonus, or 0 if
     * this device already got a bonus that day (or there is no book).
     */
    suspend fun recordDailyBonus(date: String, deviceId: String, bonus: Int): Int = db.withTransaction {
        val book = bookDao.getActive() ?: return@withTransaction 0
        val row = pointsDayDao.get(book.id, date, deviceId) ?: PointsDay(date, deviceId, updatedAt = 0, bookId = book.id)
        if (row.dailyBonus > 0) {
            0
        } else {
            pointsDayDao.upsert(row.copy(dailyBonus = bonus, updatedAt = AppClock.currentTimeMillis()))
            bonus
        }
    }

    private suspend fun addRisePoints(bookId: String, date: String, deviceId: String, points: Int, now: Long) {
        if (points == 0) return
        val row = pointsDayDao.get(bookId, date, deviceId) ?: PointsDay(date, deviceId, updatedAt = now, bookId = bookId)
        pointsDayDao.upsert(row.copy(risePoints = row.risePoints + points, updatedAt = now))
    }

    /** Counts one finished round for [date] on this device in the active book and stores the goal set today. */
    suspend fun recordRoundCompleted(date: String, deviceId: String, dailyGoal: Int) {
        db.withTransaction {
            val book = bookDao.getActive() ?: return@withTransaction
            val rounds = practiceDayDao.get(book.id, date, deviceId)?.roundsCompleted ?: 0
            practiceDayDao.upsert(PracticeDay(date, deviceId, rounds + 1, dailyGoal, AppClock.currentTimeMillis(), book.id))
        }
    }

    /** The stored entry (also an ignored one), or null if it does not exist. */
    suspend fun vocabularyById(id: String): Vocabulary? = vocabularyDao.getById(id)

    /**
     * Adds [vocabulary] to the active book. Returns false (and inserts nothing) if a non-deleted
     * entry with the same English + unit exists in that book, or if there is no book.
     */
    suspend fun addVocabulary(vocabulary: Vocabulary): Boolean = db.withTransaction {
        val book = bookDao.getActive() ?: return@withTransaction false
        val key = VocabularyImporter.vocabularyKey(vocabulary.english, vocabulary.unit)
        val duplicate = vocabularyDao.getAllIncludingDeleted().any {
            !it.isDeleted && VocabularyImporter.vocabularyKey(it.english, it.unit) == key
        }
        if (!duplicate) vocabularyDao.insert(vocabulary.copy(updatedAt = AppClock.currentTimeMillis(), bookId = book.id))
        !duplicate
    }

    suspend fun update(vocabulary: Vocabulary) =
        vocabularyDao.update(vocabulary.copy(updatedAt = AppClock.currentTimeMillis()))

    /**
     * Ignores (soft-deletes) or restores an entry; all other values, including the learning
     * level, are kept.
     */
    suspend fun setIgnored(id: String, ignored: Boolean) {
        db.withTransaction {
            val current = vocabularyDao.getById(id)
            if (current != null && current.isDeleted != ignored) update(current.copy(isDeleted = ignored))
        }
    }

    /**
     * Applies the first answer of a round: new level (see [Leitner.afterFirstAttempt]; [early]
     * = asked before it was due; "earlier day" is judged from the stored review date, [date] and
     * [zone]) and the review date, in one transaction and based on the stored level. Reads the
     * stored entry fresh, so a restore during a quiz round is not overwritten. Verb progress is
     * independent of the vocabulary entry of the same verb. Verb training is separate from the
     * rewards: it gives no points, so [deviceId] is unused and the result always has 0 points.
     */
    suspend fun recordVerbFirstAttempt(
        id: String, correct: Boolean, early: Boolean, date: String, deviceId: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): FirstAttemptResult = db.withTransaction {
        val current = verbDao.getById(id)
        if (current == null || current.isDeleted) return@withTransaction FirstAttemptResult.NONE
        val now = AppClock.currentTimeMillis()
        val earlier = Due.reviewedEarlierDay(current.lastReviewedAt, LocalDate.parse(date), zone)
        val outcome = Leitner.outcomeOfFirstAttempt(current.leitnerLevel, correct, early, reviewedEarlierDay = earlier)
        verbDao.update(current.copy(leitnerLevel = outcome.newLevel, lastReviewedAt = AppClock.nowMillis(zone), updatedAt = now))
        FirstAttemptResult(0, rose = outcome.rose, outcome = outcome)
    }

    /**
     * Like [recordVerbFirstAttempt] for vocabulary, with the answer [form] and "Auch richtig" /
     * "Fast richtig" ([kept]) of a typing question (see [Leitner.afterFirstAttempt]). Also stores the
     * first correctly typed answer once (see [SelfTyped]). Review and typed dates come from
     * [AppClock] (shifted in the test app), updatedAt keeps the real time. The points
     * go to the book of the word.
     */
    suspend fun recordFirstAttempt(
        id: String, correct: Boolean, early: Boolean, date: String, deviceId: String,
        form: AnswerForm = AnswerForm.MULTIPLE_CHOICE, kept: KeptAnswer? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): FirstAttemptResult = db.withTransaction {
        val current = vocabularyDao.getById(id)
        if (current == null || current.isDeleted) return@withTransaction FirstAttemptResult.NONE
        val now = AppClock.currentTimeMillis()
        // Multiple choice raises typeable words only up to Leitner.CHOICE_MAX_TYPEABLE; typing goes on to 5.
        val maxLevel = Leitner.choiceMaxLevel(current.toQuizWord().isTypeable)
        val earlier = Due.reviewedEarlierDay(current.lastReviewedAt, LocalDate.parse(date), zone)
        val outcome = Leitner.outcomeOfFirstAttempt(current.leitnerLevel, correct, early, maxLevel, earlier, form, kept)
        val reviewedAt = AppClock.nowMillis(zone)
        val updated = current.withLevel(outcome.newLevel).copy(lastReviewedAt = reviewedAt, updatedAt = now)
        vocabularyDao.update(if (SelfTyped.counts(form, correct, kept)) updated.withFirstTypedCorrect(reviewedAt) else updated)
        val points = Points.forFirstAttempt(
            current.leitnerLevel, current.highestLevel, correct, early, maxLevel, earlier, form, kept
        )
        addRisePoints(current.bookId, date, deviceId, points, now)
        FirstAttemptResult(points, rose = outcome.rose, outcome = outcome)
    }

    /** Admin mode: sets the given vocabulary to [level] (see [LevelReset.vocabularyTo]); returns the number of changed entries. */
    suspend fun setVocabularyLevels(ids: Collection<String>, level: Int): Int = db.withTransaction {
        val changed = LevelReset.vocabularyTo(
            vocabularyDao.getAllIncludingDeleted(), ids.toSet(), level, AppClock.currentTimeMillis()
        )
        vocabularyDao.updateAll(changed)
        changed.size
    }

    /**
     * Gives every active vocabulary entry of every book a core word (see [CoreWords.normalize]):
     * derived if unambiguous, otherwise the entry is ignored. Idempotent; levels and updatedAt stay.
     * Runs at the app start and after every import and restore. Returns how many entries were ignored.
     */
    suspend fun ensureCoreWords(): Int = db.withTransaction {
        val changed = CoreWords.normalizeAll(vocabularyDao.getAllBooksIncludingDeleted())
        vocabularyDao.updateAll(changed)
        changed.count { it.isDeleted }
    }

    /**
     * Parses and validates the whole file first, then applies it in one transaction to the book
     * the file names: an unknown book is created (active only if there is no active book, the
     * caller asks to switch otherwise), a known one gets the file's title. Duplicates are looked
     * for only within that book; existing entries follow the file (see [VocabularyImporter.plan]).
     */
    suspend fun importVocabulary(json: String): BookImport = applyImport(VocTrainerJson.parseImport(json))

    /**
     * Applies the example book ([ExampleBook]) like an import, without the "switch book?" question:
     * created if missing (active only if no book is active, e.g. on a fresh install), otherwise only
     * completed (see [VocabularyImporter.plan]), so running it again changes nothing. Progress, ignored
     * entries and the core words of existing entries stay. Runs at the app start and after
     * "Sicherung wiederherstellen". Returns null without [exampleBook].
     */
    suspend fun ensureExampleBook(): BookImport? {
        val json = exampleBook?.invoke() ?: return null
        return applyImport(VocTrainerJson.parseImport(json), isExampleBook = true)
    }

    private suspend fun applyImport(file: VocTrainerFile, isExampleBook: Boolean = false): BookImport {
        val bookId = checkNotNull(file.bookId)
        val title = checkNotNull(file.bookTitle)
        return db.withTransaction {
            val existing = bookDao.getById(bookId)
            val book = when {
                existing == null -> Book(
                    bookId, title, file.language ?: Book.DEFAULT_LANGUAGE, isActive = bookDao.getActive() == null
                ).also { bookDao.insert(it) }
                existing.title != title -> existing.copy(title = title).also { bookDao.update(it) }
                else -> existing
            }
            val plan = VocabularyImporter.plan(
                file,
                vocabularyDao.getAllOfBookIncludingDeleted(book.id),
                verbDao.getAllOfBookIncludingDeleted(book.id),
                AppClock.currentTimeMillis(),
                exampleBook = isExampleBook
            )
            vocabularyDao.insertAll(plan.newVocabulary.map { it.copy(bookId = book.id) })
            vocabularyDao.updateAll(plan.completedVocabulary)
            verbDao.insertAll(plan.newVerbs.map { it.copy(bookId = book.id) })
            verbDao.updateAll(plan.updatedVerbs)
            BookImport(
                plan.result.copy(ignoredWithoutCoreWord = ensureCoreWords()),
                book.id, book.title, isNewBook = existing == null, isActive = book.isActive
            )
        }
    }

    /**
     * The backup file: every book except the example book (nothing of it is written, see
     * [ExampleBook]), everything in insertion order, the learner's [settings] (if given) and the
     * app variant and version that wrote it. If the example book is active, the file has no active book (the
     * restore then makes the first book active); with only the example book it has no book at all.
     */
    suspend fun exportSync(settings: SettingsBackup? = null): String = db.withTransaction {
        fun real(bookId: String) = bookId != ExampleBook.ID
        VocTrainerJson.encodeSync(
            bookDao.getAll().filter { real(it.id) },
            vocabularyDao.getAllBooksIncludingDeletedInInsertionOrder().filter { real(it.bookId) },
            verbDao.getAllBooksIncludingDeletedInInsertionOrder().filter { real(it.bookId) },
            practiceDayDao.getAllBooks().filter { real(it.bookId) }, pointsDayDao.getAllBooks().filter { real(it.bookId) },
            figureDao.getAllBooks().filter { real(it.bookId) }, badgeDao.getAllBooks().filter { real(it.bookId) },
            settings = settings, appVariant = VocTrainerJson.appVariant(BuildConfig.DEBUG),
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    private fun parseBackup(json: String): SyncData =
        VocTrainerJson.parseSync(json, allowNegativeRisePoints = BuildConfig.DEBUG)

    /** Parses and validates a backup file without changing anything (for the confirmation). */
    fun backupInfo(json: String): BackupInfo = parseBackup(json).info()

    /**
     * "Sicherung wiederherstellen": replaces the complete learning state with the backup, no
     * merge rules. Parses and validates the whole file first (on an error nothing changes), then
     * deletes all books, vocabulary (also ignored), verbs, practice and points days, figures and
     * badges and inserts the backup exactly as stored (including which book is active, see
     * [withOneActive]), in one transaction; the stored updatedAt values stay. The example book is
     * not in the file; it is added again fresh, active only if no book is active (see
     * [ensureExampleBook]), so its progress starts anew. Afterwards the settings of the file (if any) are taken over
     * and the figure stages of all books are recomputed and marked as shown, so a restore is no
     * "new stage" moment; badges the restored state of the active book justifies are awarded
     * silently. The caller reschedules the reminder.
     */
    suspend fun restoreBackup(json: String, settings: AppSettings): BackupInfo {
        val data = parseBackup(json)
        // Before the books arrive: a restored phone gets no welcome.
        settings.finishFirstStart()
        val previousSlots = db.withTransaction {
            val slots = figureDao.getAllBooks().map { it.bookId to it.slot }
            bookDao.deleteAll()
            vocabularyDao.deleteAll()
            verbDao.deleteAll()
            practiceDayDao.deleteAll()
            pointsDayDao.deleteAll()
            figureDao.deleteAll()
            badgeDao.deleteAll()
            bookDao.insertAll(data.books)
            vocabularyDao.insertAll(data.vocabulary)
            verbDao.insertAll(data.irregularVerbs)
            practiceDayDao.upsertAll(data.practiceDays)
            pointsDayDao.upsertAll(data.pointsDays)
            figureDao.upsertAll(data.figures)
            badgeDao.upsertAll(data.badges)
            slots
        }
        val ignored = ensureCoreWords()
        // The backup has no example book: it comes back fresh (inactive unless no book is active).
        ensureExampleBook()
        data.settings?.let(settings::restoreBackup)
        for (book in data.books) updateFigureStageOf(book.id)
        checkBadges(settings)
        val restored = figureDao.getAllBooks()
        for ((bookId, slot) in previousSlots) settings.setLastShownFigureStage(bookId, slot, 0)
        for (figure in restored) settings.setLastShownFigureStage(figure.bookId, figure.slot, figure.stage)
        settings.markRestored()
        val info = data.info()
        val books = info.books.zip(data.books) { counts, book ->
            counts.copy(vocabulary = vocabularyDao.getAllOfBookIncludingDeleted(book.id).count { !it.isDeleted })
        }
        return info.copy(books = books, ignoredWithoutCoreWord = ignored)
    }

    companion object {
        /** See [checkBadges]; every ViewModel has its own repository. */
        private val badgeLock = Mutex()

        /** The book of the test tool "Zweites Testbuch anlegen". */
        const val TEST_BOOK_ID = "debug-testbuch"
        const val TEST_BOOK_TITLE = "Testbuch"
    }
}

/** The invented content of the test book (test app only): simple words, no textbook content. */
private object TestBook {
    private class Word(val english: String, val german: List<String>, val coreWord: String, val type: WordType, val part: String)

    private val words = listOf(
        Word("apple", listOf("Apfel"), "apple", WordType.NOUN, "A"),
        Word("(to) jump", listOf("springen"), "jump", WordType.VERB, "A"),
        Word("green", listOf("grün"), "green", WordType.OTHER, "A"),
        Word("window", listOf("Fenster"), "window", WordType.NOUN, "A"),
        Word("(to) laugh", listOf("lachen"), "laugh", WordType.VERB, "A"),
        Word("kitchen", listOf("Küche"), "kitchen", WordType.NOUN, "B"),
        Word("quiet", listOf("leise", "ruhig"), "quiet", WordType.OTHER, "B"),
        Word("bicycle", listOf("Fahrrad"), "bicycle", WordType.NOUN, "B"),
        Word("(to) whisper", listOf("flüstern"), "whisper", WordType.VERB, "B"),
        Word("a piece of cake", listOf("ein Kinderspiel"), "cake", WordType.PHRASE, "B")
    )

    fun vocabulary(bookId: String, now: Long): List<Vocabulary> = words.map {
        Vocabulary(
            english = it.english, germanMeanings = it.german, unit = 1, part = it.part,
            page = if (it.part == "A") 10 else 11, wordType = it.type, coreWord = it.coreWord,
            updatedAt = now, bookId = bookId
        )
    }

    fun verbs(bookId: String, now: Long): List<IrregularVerb> = listOf(
        IrregularVerb(
            infinitive = "(to) swim", simplePast = "swam", pastParticiple = "swum", germanMeanings = listOf("schwimmen"),
            unit = 1, part = "A", page = 10, updatedAt = now, bookId = bookId
        ),
        IrregularVerb(
            infinitive = "(to) fly", simplePast = "flew", pastParticiple = "flown", germanMeanings = listOf("fliegen"),
            unit = 1, part = "B", page = 11, updatedAt = now, bookId = bookId
        )
    )
}
