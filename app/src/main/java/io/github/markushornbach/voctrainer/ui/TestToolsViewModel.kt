package io.github.markushornbach.voctrainer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.badgeValues
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.avatarPlan
import io.github.markushornbach.voctrainer.data.current
import io.github.markushornbach.voctrainer.data.ofStart
import io.github.markushornbach.voctrainer.data.keys
import io.github.markushornbach.voctrainer.data.quizFilter
import io.github.markushornbach.voctrainer.data.toQuizWord
import io.github.markushornbach.voctrainer.data.toRecord
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import io.github.markushornbach.voctrainer.learning.Streak
import io.github.markushornbach.voctrainer.learning.AlmostKind
import io.github.markushornbach.voctrainer.learning.AlsoCorrectIndex
import io.github.markushornbach.voctrainer.learning.AnswerChecker
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.Due
import io.github.markushornbach.voctrainer.learning.LearningMaterial
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.Typo
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TestToolsState(
    val loaded: Boolean = false,
    /** Stage of the figure being grown; null = none chosen. */
    val stage: Int? = null,
    val figureCount: Int = 0,
    val totalPoints: Int = 0,
    val mode: QuizMode = QuizMode.VOCABULARY,
    /** "Datum verschieben": days added to the real date (0 = real date). */
    val dateShiftDays: Int = 0
)

/** "Prüfung testen" shows at most this many entries to pick from. */
const val CHECK_CANDIDATE_LIMIT = 10

/** An entry to pick in "Prüfung testen". */
data class CheckCandidate(val id: String, val english: String)

/** "Prüfung testen": [result] is the line for the last "Prüfen" (null until then or after a change). */
data class CheckState(
    val search: String = "",
    val wordId: String? = null,
    val wordEnglish: String? = null,
    val input: String = "",
    val result: String? = null
)

/**
 * Page "Testwerkzeuge" – test app (debug build) only. Every action returns at once in a release
 * build; the repository functions are guarded as well.
 */
class TestToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabularyRepository(VocTrainerDatabase.getInstance(application))
    private val settings = AppSettings.getInstance(application)

    val state: StateFlow<TestToolsState> = combine(
        repository.figures, repository.figureSlotStart, repository.totalPoints, settings.quizMode, AppClock.offsetDays
    ) { figures, slotStart, total, mode, shift ->
        TestToolsState(true, figures.current(slotStart)?.stage, figures.size, total, mode, shift)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TestToolsState())

    val markCorrectAnswer: StateFlow<Boolean> = settings.markCorrectAnswer

    private val _check = MutableStateFlow(CheckState())

    /** "Prüfung testen": search, picked entry, input and result. */
    val check: StateFlow<CheckState> = _check.asStateFlow()

    /** The entries to pick from for "Prüfung testen" (same search as "Wortdetails"). */
    val checkCandidates: StateFlow<List<CheckCandidate>> = combine(
        repository.vocabularyInInsertionOrder, _check
    ) { words, check ->
        if (check.search.isBlank()) emptyList()
        else TestToolsTexts.matching(words, check.search).take(CHECK_CANDIDATE_LIMIT).map { CheckCandidate(it.id, it.english) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The same lookups as a vocabulary round: all vocabulary and all irregular verbs. */
    private val lookups = combine(repository.vocabularyInInsertionOrder, repository.verbsInInsertionOrder) { words, verbs ->
        val pool = words.map { it.toQuizWord() }
        Triple(pool, AlsoCorrectIndex(pool), LearningMaterial(pool, verbs.map { it.toQuizWord() }))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCheckSearch(text: String) {
        _check.value = _check.value.copy(search = text)
    }

    fun pickCheckWord(candidate: CheckCandidate) {
        _check.value = _check.value.copy(wordId = candidate.id, wordEnglish = candidate.english, result = null)
    }

    fun setCheckInput(text: String) {
        _check.value = _check.value.copy(input = text.replace("\n", ""), result = null)
    }

    /** "Prüfen": the real checker; never changes the learning state. */
    fun runCheck() {
        if (!BuildConfig.DEBUG) return
        val check = _check.value
        val id = check.wordId ?: return
        viewModelScope.launch {
            val (pool, index, material) = lookups.filterNotNull().first()
            val word = pool.firstOrNull { it.id == id }
            val result = when {
                word == null -> TestToolsTexts.CHECK_NO_WORD
                check.input.isBlank() -> EMPTY_INPUT_HINT
                else -> withContext(Dispatchers.Default) { TestToolsTexts.checkResult(check.input.trim(), word, index, material) }
            }
            _check.value = _check.value.copy(result = result)
        }
    }

    /** "Datum verschieben": [days] more than the current shift. */
    fun shiftDate(days: Int) = setDateShift(AppClock.offsetDays.value + days)

    /** "Echtes Datum": back to the real date. */
    fun resetDate() = setDateShift(0)

    private fun setDateShift(days: Int) = perform {
        settings.setDateShiftDays(days)
        TestToolsTexts.dateShifted(days, AppClock.today())
    }

    private val _message = MutableStateFlow<String?>(null)

    /** Snackbar text confirming the last action. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun setStage(stage: Int) = perform {
        val figure = repository.setFigureStageForTest(stage)
        when {
            figure == null -> TestToolsTexts.NO_FIGURE
            figure.isFinished -> TestToolsTexts.FIGURE_FINISHED
            else -> "Stufe ${figure.stage} gesetzt"
        }
    }

    fun finishFigure() = setStage(FigureRules.MAX_STAGE)

    fun resetFigures() = perform { TestToolsTexts.figuresDeleted(repository.deleteAllFiguresForTest()) }

    /** [input] from the number field; recomputes the stage as after a round (never lowers it). */
    fun setPoints(input: String) {
        val target = input.trim().toIntOrNull()?.takeIf { it >= 0 }
        if (target == null) {
            _message.value = TestToolsTexts.INVALID_POINTS
            return
        }
        perform {
            repository.setTotalPointsForTest(AppClock.today().toString(), settings.deviceId, target)
            val growth = repository.updateFigureStage()
            TestToolsTexts.pointsSet(target, growth?.stage)
        }
    }

    private val _levelPick = MutableStateFlow(LevelPick())

    /** The toggle and the last level set of "Lernlevel der Auswahl setzen" (session only). */
    val levelPick: StateFlow<LevelPick> = _levelPick.asStateFlow()

    init {
        // A changed quiz selection (or book) clears the highlight of the last level set.
        viewModelScope.launch {
            repository.activeBook
                .map { book -> Triple(book?.id, book?.quizFilter(QuizMode.VOCABULARY), book?.quizFilter(QuizMode.VERBS)) }
                .distinctUntilChanged()
                .drop(1)
                .collect { _levelPick.value = _levelPick.value.afterSelectionChange() }
        }
    }

    /** The toggle Verben / Vokabeln. */
    fun setLevelMode(mode: QuizMode) {
        _levelPick.value = _levelPick.value.withMode(mode)
    }

    fun setVerbLevel(level: Int) = perform {
        val ids = selection(QuizMode.VERBS).map { it.id }
        if (ids.isEmpty()) TestToolsTexts.EMPTY_SELECTION
        else {
            _levelPick.value = _levelPick.value.afterSet(QuizMode.VERBS, level)
            TestToolsTexts.verbLevelSet(repository.setVerbLevels(ids, level), level)
        }
    }

    /** Sets every word of the vocabulary selection (as the quiz counts it) to [level] and makes it due. */
    fun setVocabularyLevel(level: Int) = perform {
        val ids = selection(QuizMode.VOCABULARY).map { it.id }
        if (ids.isEmpty()) TestToolsTexts.EMPTY_SELECTION
        else {
            _levelPick.value = _levelPick.value.afterSet(QuizMode.VOCABULARY, level)
            VocabularyEditor.levelSetMessage(repository.setVocabularyLevels(ids, level), level)
        }
    }

    /** For the selection of the current quiz mode. */
    fun makeSelectionDue() = perform {
        val mode = settings.quizMode.value
        val ids = selection(mode).map { it.id }
        if (mode == QuizMode.VERBS) repository.makeVerbsDue(ids) else repository.makeVocabularyDue(ids)
        if (ids.isEmpty()) TestToolsTexts.EMPTY_SELECTION else TestToolsTexts.dueNow(ids.size, mode)
    }

    /** "Alle Vokabeln entfernen" (after the confirmation): deletes all vocabulary and verbs for a fresh import. */
    fun deleteAllVocabulary() = perform { TestToolsTexts.vocabularyDeleted(repository.deleteAllVocabularyForTest()) }

    /** "Zweites Testbuch anlegen": an inactive book with invented words; nothing happens if it exists. */
    fun createTestBook() = perform {
        if (repository.createTestBookForTest()) TestToolsTexts.TEST_BOOK_CREATED else TestToolsTexts.TEST_BOOK_EXISTS
    }

    /** "Ein Tipp pro Besuch" (default on): switched off, any number of tips may follow each other. */
    val tipsOnePerVisit: StateFlow<Boolean> = settings.tipsOnePerVisit

    fun setTipsOnePerVisit(enabled: Boolean) {
        if (BuildConfig.DEBUG) settings.setTipsOnePerVisit(enabled)
    }

    /** "Erster Start simulieren": welcome, book choice and avatar choice again; no data is deleted. */
    fun simulateFirstStart() {
        if (BuildConfig.DEBUG) settings.simulateFirstStart()
    }

    val lastReleaseNotesVersion: StateFlow<String?> = settings.lastReleaseNotesVersion

    fun simulateReleaseNotesUpdate() = perform {
        settings.simulateReleaseNotesUpdate()
        TestToolsTexts.NOTES_UPDATE_DONE
    }

    fun clearReleaseNotesVersion() = perform {
        settings.endReleaseNotesUpdateSimulation()
        settings.setLastReleaseNotesVersion(null)
        TestToolsTexts.NOTES_VERSION_CLEARED
    }

    /** "Alle Tipps zurücksetzen": the same as "Tipps wieder zeigen" on the help page. */
    fun resetTips() = perform {
        settings.resetTips()
        TipTexts.SHOWN_AGAIN
    }

    /** "Abzeichen": one line per series with the current value and the earned tiers. */
    val badgeLines: StateFlow<List<String>> = combine(
        repository.badges, repository.vocabularyInInsertionOrder, repository.practiceDays,
        AppClock.offsetDays
    ) { badges, words, days, _ ->
        val longest = Streak.compute(days.map { it.toRecord() }, AppClock.today(), settings.deviceId, settings.dailyGoal.value).longest
        val earned = badges.keys()
        BadgeRules.progress(badgeValues(words, longest), earned).map { row ->
            BadgeTexts.testLine(row, earned.filter { it.series == row.series }.map { it.tier })
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Avatar-Modell": the numbers behind the requirements of the current avatar (see [TestToolsTexts.avatarModel]). */
    val avatarModelLines: StateFlow<List<String>> = combine(
        repository.figures, repository.figureSlotStart, repository.totalPoints, repository.vocabularyInInsertionOrder
    ) { figures, slotStart, total, words ->
        TestToolsTexts.avatarModel(figures, slotStart, total, words.map { it.toQuizWord() })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Earned and possible badge tiers, for the header "Abzeichen (7 von 12)". */
    val badgeCount: StateFlow<Pair<Int, Int>> = repository.badges.map {
        it.keys().size to BadgeSeries.entries.sumOf { series -> series.thresholds.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to BadgeSeries.entries.sumOf { it.thresholds.size })

    /** "Abzeichen zurücksetzen": deletes all badges; the next check awards them again (silently). */
    fun resetBadges() = perform { BadgeTexts.testDeleted(repository.deleteAllBadgesForTest()) }

    /** "Abzeichen jetzt prüfen": the same check as at the start of the app. */
    fun checkBadgesNow() = perform { BadgeTexts.testAwarded(repository.checkBadges(settings)) }

    fun setMarkCorrectAnswer(enabled: Boolean) {
        if (BuildConfig.DEBUG) settings.setMarkCorrectAnswer(enabled)
    }

    /** The saved selection of [mode], exactly as the quiz start screen counts it. */
    private suspend fun selection(mode: QuizMode): List<QuizWord> {
        val words = when (mode) {
            QuizMode.VOCABULARY -> repository.vocabularyInInsertionOrder.first().map { it.toQuizWord() }
            QuizMode.VERBS -> repository.verbsInInsertionOrder.first().map { it.toQuizWord() }
        }
        val filter = QuizSelection.normalize(words, repository.loadQuizFilter(mode) ?: QuizFilter())
        return QuizSelection.filter(words, filter)
    }

    private fun perform(action: suspend () -> String) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { _message.value = action() }
    }
}

/** German snackbar texts of the page "Testwerkzeuge". */
object TestToolsTexts {
    const val ALL_INFO_PAGES = "Alle Info-Seiten"
    const val SIMULATE_NOTES_UPDATE = "Update simulieren (Versionshinweise)"
    const val CLEAR_NOTES_VERSION = "Gespeicherte Version löschen"
    const val NOTES_UPDATE_DONE = "Versionshinweise kommen auf der Startseite"
    const val NOTES_VERSION_CLEARED = "Gespeicherte Version gelöscht"

    fun releaseNotesStored(version: String?) = "Versionshinweise gespeichert: ${version ?: "keine"}"

    /** Info block "Avatar-Modell": [words] are the active entries of the book, [slotStart] its [io.github.markushornbach.voctrainer.data.Book.figureSlotStart]. */
    fun avatarModel(figures: List<Figure>, slotStart: Int, totalPoints: Int, words: List<QuizWord>): List<String> {
        val counts = FigureRules.countWords(words)
        val plan = figures.avatarPlan(counts.askable, slotStart)
        val current = figures.current(slotStart)
        val counted = figures.ofStart(slotStart).count { it.isFinished }
        val since = current?.let { FigureRules.pointsSinceStart(totalPoints, it.pointsAtStart) } ?: 0
        val stage = current?.stage ?: FigureRules.MAX_STAGE
        val next = plan.requirement(stage + 1).takeIf { stage < FigureRules.MAX_STAGE }
        return listOf(
            "Avatar Nr. ${plan.number} (fertige: ${figures.count { it.isFinished }}, aufgegebene: ${figures.count { !it.isFinished && it != current }})",
            "figureSlotStart $slotStart · fertige, die zählen: $counted · fertige, die nicht zählen: ${figures.count { it.isFinished } - counted}",
            "Wörter: ${counts.askable} fragbar, ${counts.sure} gelernt",
            "Obergrenze ${plan.cap} · Basis ${plan.base} · verfügbar ${plan.available} · Faktor ${"%.2f".format(java.util.Locale.ROOT, plan.scale)}",
            if (current == null) "Gespeicherte Stufe: kein aktueller Avatar"
            else "Stufe gespeichert ${current.stage} · nach den Regeln ${FigureRules.computedStage(since, counts, plan.number)} (ohne „nie senken“)",
            if (next == null) "Nächste Stufe: –" else "Nächste Stufe ${stage + 1}: ${next.points} Punkte (seit Start: $since), ${next.sureWords} gelernte Wörter",
            "Neuer Avatar wählbar: ${if (plan.canStart) "ja" else "nein"}"
        )
    }

    const val NO_FIGURE = "Kein Avatar ausgewählt"
    const val FIGURE_FINISHED = "Stufe 10 gesetzt – Avatar ist fertig"
    const val INVALID_POINTS = "Bitte eine Zahl ab 0 eingeben"
    const val EMPTY_SELECTION = "Die Auswahl ist leer"
    const val CREATE_TEST_BOOK = "Zweites Testbuch anlegen"
    const val TEST_BOOK_CREATED = "Testbuch angelegt (inaktiv) – wechseln in „Vokabeln verwalten“"
    const val TEST_BOOK_EXISTS = "Das Testbuch gibt es schon"

    /** "Tipp sofort zeigen" of "Verben üben" without verbs in the example book. */
    const val NO_VERBS_FOR_TIP = "Das Beispielbuch hat keine Verben."

    /** Where "Tipp sofort zeigen" shows [tip] (always with the example book, see [QuizViewModel.showTipDemo]). */
    fun tipSituation(tip: Tip): String = when (tip) {
        Tip.SELECTION, Tip.START_BUTTON, Tip.ALL_KNOWN -> "Startseite"
        Tip.AVATAR_CARD -> "Startseite (ohne Avatar erst die Avatar-Wahl)"
        Tip.LEVELS -> "Startseite, Vokabeln (sonst erst nach N3); nach „Verstanden“ folgt N4 (Kette)"
        Tip.VERBS -> "Startseite, wechselt zu Verben (ohne Verben nur ein Hinweis)"
        Tip.REPEAT -> "Demo-Runde, die mit Wiederholungen beginnt; nichts wird gespeichert"
        Tip.TYPING -> "Demo-Runde mit Fragen zum Tippen; nichts wird gespeichert"
        Tip.ROUND_RESULT -> "Ergebnis einer erfundenen Runde; nichts wird gespeichert"
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun date(day: LocalDate): String = day.format(dateFormat)

    /** Marker on the main screen while the date is shifted, e.g. "Test: 18.10.2026". */
    fun simulatedDateMarker(day: LocalDate): String = "Test: ${date(day)}"

    /** "Heute: 18.10.2026 (echtes Datum + 7 Tage)" on the test tools page. */
    fun simulatedToday(days: Int, today: LocalDate): String =
        "Heute: ${date(today)} " + if (days == 0) "(echtes Datum)" else "(echtes Datum ${signed(days)} ${dayWord(days)})"

    fun dateShifted(days: Int, today: LocalDate): String =
        if (days == 0) "Echtes Datum: ${date(today)}" else "Simuliertes Datum: ${date(today)}"

    private fun signed(days: Int): String = if (days > 0) "+ $days" else "− ${-days}"

    private fun dayWord(days: Int): String = if (days == 1 || days == -1) "Tag" else "Tage"

    const val CHECK_NO_WORD = "Eintrag nicht gefunden"

    /** The search of "Wortdetails" and "Prüfung testen": English or German, contains, ignoring case. */
    fun matching(words: List<Vocabulary>, search: String): List<Vocabulary> {
        val query = search.trim()
        return words.filter { word ->
            query.isEmpty() || word.english.contains(query, ignoreCase = true) ||
                word.germanMeanings.any { it.contains(query, ignoreCase = true) }
        }
    }

    /**
     * The result line of "Prüfung testen" for [answer] to [word], with the same checker and lookups
     * as the quiz: "Richtig", "Auch richtig (…)", "Fast richtig – …" or "Falsch – <Grund>".
     */
    fun checkResult(answer: String, word: QuizWord, index: AlsoCorrectIndex, material: LearningMaterial): String {
        if (!word.isTypeable) return "Falsch – nicht tippbar"
        return when (val result = AnswerChecker.checkTyped(answer, word, index, material)) {
            AnswerChecker.TypedResult.Correct -> "Richtig"
            is AnswerChecker.TypedResult.AlsoCorrect -> "Auch richtig (Eintrag „${result.other.english}“)"
            is AnswerChecker.TypedResult.AlmostCorrect -> when (result.kind) {
                AlmostKind.TYPO -> "Fast richtig – Tippfehler (${position(result)})"
                AlmostKind.AMERICAN -> "Fast richtig – amerikanisch (Regel ${result.rules.joinToString(", ")})"
                AlmostKind.TYPO_OF_OTHER ->
                    "Fast richtig – Gesucht war „${word.english}“ (Tippfehler von „${result.other?.english}“, ${position(result)})"
            }
            AnswerChecker.TypedResult.Wrong -> "Falsch – " + when (val why = AnswerChecker.whyWrong(answer, word, index, material)) {
                AnswerChecker.WrongReason.NotTypeable -> "nicht tippbar"
                AnswerChecker.WrongReason.TooShort -> "Wort kürzer als ${Typo.MIN_LETTERS} Buchstaben"
                AnswerChecker.WrongReason.TooManyErrors -> "mehr als ein Fehler"
                is AnswerChecker.WrongReason.MaterialWord -> "ergibt Lernstoff-Wort „${why.word}“"
            }
        }
    }

    /** "Stelle 3" / "Stelle 3–4" (1-based, in the correct spelling; an extra letter in the input). */
    private fun position(result: AnswerChecker.TypedResult.AlmostCorrect): String {
        val range = result.diff.expected.takeIf { !it.isEmpty() } ?: result.diff.given
        return if (range.first == range.last) "Stelle ${range.first + 1}" else "Stelle ${range.first + 1}–${range.last + 1}"
    }

    fun vocabularyDeleted(count: Int): String =
        if (count == 0) "Keine Vokabeln vorhanden" else "$count Einträge (Vokabeln und Verben) entfernt"

    fun figuresDeleted(count: Int): String = when (count) {
        0 -> "Kein Avatar vorhanden"
        1 -> "1 Avatar gelöscht"
        else -> "$count Avatare gelöscht"
    }

    /** "Punkte auf 1.234 gesetzt", plus the new stage if the figure grew. */
    fun pointsSet(points: Int, grownTo: Int?): String =
        "Punkte auf ${PracticeTexts.formatNumber(points)} gesetzt" + (grownTo?.let { " · Avatar jetzt Stufe $it" } ?: "")

    fun verbLevelSet(changed: Int, level: Int): String = when (changed) {
        0 -> "Keine Änderung – alle Verben sind schon auf Level $level"
        1 -> "1 Verb auf Level $level gesetzt"
        else -> "$changed Verben auf Level $level gesetzt"
    }

    /** "25 Wörter sind jetzt fällig" / "1 Verb ist jetzt fällig". */
    fun dueNow(count: Int, mode: QuizMode): String {
        val noun = when (mode) {
            QuizMode.VOCABULARY -> if (count == 1) "Wort" else "Wörter"
            QuizMode.VERBS -> if (count == 1) "Verb" else "Verben"
        }
        return "$count $noun " + (if (count == 1) "ist" else "sind") + " jetzt fällig"
    }
}
