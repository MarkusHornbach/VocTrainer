package io.github.markushornbach.voctrainer.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.data.badgeValues
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.FigureGrowth
import io.github.markushornbach.voctrainer.data.keys
import io.github.markushornbach.voctrainer.learning.BadgeKey
import io.github.markushornbach.voctrainer.learning.BadgeProgress
import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.data.FirstAttemptResult
import io.github.markushornbach.voctrainer.data.avatarPlan
import io.github.markushornbach.voctrainer.data.collection
import io.github.markushornbach.voctrainer.data.current
import io.github.markushornbach.voctrainer.data.IrregularVerb
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.toQuizWord
import io.github.markushornbach.voctrainer.data.toRecord
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.quizFilter
import io.github.markushornbach.voctrainer.learning.AlsoCorrectIndex
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.AnswerStep
import io.github.markushornbach.voctrainer.learning.AttemptRecord
import io.github.markushornbach.voctrainer.learning.Due
import io.github.markushornbach.voctrainer.learning.FigureWiggle
import io.github.markushornbach.voctrainer.learning.FirstRound
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.KeptAnswer
import io.github.markushornbach.voctrainer.learning.LearningMaterial
import io.github.markushornbach.voctrainer.learning.LearningProgress
import io.github.markushornbach.voctrainer.learning.LevelReason
import io.github.markushornbach.voctrainer.learning.RoundStars
import io.github.markushornbach.voctrainer.learning.QuestionDirection
import io.github.markushornbach.voctrainer.learning.LevelOutcome
import io.github.markushornbach.voctrainer.learning.MultipleChoiceQuestion
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.NextStage
import io.github.markushornbach.voctrainer.learning.Reach
import io.github.markushornbach.voctrainer.learning.Reachability
import io.github.markushornbach.voctrainer.learning.MultipleChoiceGenerator
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.QuestionGenerator
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.ReaskResult
import io.github.markushornbach.voctrainer.learning.RegularVerbs
import io.github.markushornbach.voctrainer.learning.RoundDetail
import io.github.markushornbach.voctrainer.learning.RoundReport
import io.github.markushornbach.voctrainer.learning.RoundQuestion
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.SelectionProgress
import io.github.markushornbach.voctrainer.learning.Streak
import io.github.markushornbach.voctrainer.learning.StreakInfo
import io.github.markushornbach.voctrainer.learning.VerbDistractorGenerator
import io.github.markushornbach.voctrainer.learning.VerbForms
import io.github.markushornbach.voctrainer.learning.VerbQuestion
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.VerbFormMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.speech.Speaker
import io.github.markushornbach.voctrainer.speech.SpeechState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import kotlin.random.Random

/** Fewest words a book needs for questions with suggestions (the word and its distractors). */
internal const val MIN_VOCABULARY = MultipleChoiceGenerator.DISTRACTOR_COUNT + 1
private const val CORRECT_ANSWER_DELAY_MS = 1000L

/** Longest wait after a correct answer while the solution is still being spoken. */
private const val SPOKEN_ANSWER_MAX_DELAY_MS = 3000L

/** "Auch richtig! Gesucht war: …" needs time to read. */
private const val ALSO_CORRECT_DELAY_MS = 3000L

/** "Mit Vorschlägen" appears this long after a level 3 typing question is shown (no visible timer). */
const val AUSWAHL_DELAY_LEVEL3_MS = 3000L

/** From level 4 "Mit Vorschlägen" costs a drop back to level 3, so the learner gets more time to think. */
const val AUSWAHL_DELAY_HIGHER_MS = 5000L

/** The delay before "Mit Vorschlägen" appears for a word on [level] when the question is shown. */
fun auswahlDelayMs(level: Int): Long =
    if (level > Leitner.CHOICE_MAX_TYPEABLE) AUSWAHL_DELAY_HIGHER_MS else AUSWAHL_DELAY_LEVEL3_MS

/** "🧪 +100 Punkte" on "Mein Avatar", test app only. */
private const val TEST_POINTS = 100

/** "Tipp sofort zeigen" of "Wiederholung": words of the demo round, all asked as repetitions. */
private const val REPEAT_DEMO_WORDS = 3
private const val LOG_TAG = "VocTrainer"

data class QuizStartState(
    val loaded: Boolean = false,
    val mode: QuizMode = QuizMode.VOCABULARY,
    val unitChoices: List<UnitChoice> = listOf(UnitChoice.All),
    val partOptions: List<String?> = emptyList(),
    val pages: List<Int> = emptyList(),
    val filter: QuizFilter = QuizFilter(),
    val wordCount: Int = 0,
    /** Progress of exactly the items counted in [wordCount]. */
    val progress: SelectionProgress = SelectionProgress.EMPTY,
    /** Vocabulary: fewer than 4 words exist. */
    val tooFewWords: Boolean = false,
    /** Verbs: no irregular verbs imported yet. */
    val noVerbs: Boolean = false,
    /** Verbs with "3 Formen": verbs exist, but none has a past participle. */
    val noThreeFormVerbs: Boolean = false,
    /** "Verbformen" is "3 Formen" (verb texts and level groups name both forms). */
    val threeVerbForms: Boolean = false,
    /** Vocabulary: the selection contains typeable words (typed from level 3). */
    val hasTypeable: Boolean = false,
    /** "Nächste Runde: …" about the round the next start uses; null = no line. */
    val nextRoundLine: String? = null,
    /** A vocabulary round of the active book was finished ([FirstRound.done]); true until known. */
    val firstRoundDone: Boolean = true
) {
    /** Verbs: nothing to practise, a short line instead of the selection. */
    val verbsMissing: Boolean get() = noVerbs || noThreeFormVerbs
}

/** The growing figure for the start screen, the choice and the page "Mein Avatar". */
data class FigureState(
    val loaded: Boolean = false,
    /** A figure belongs to a book: without an active book there is nothing to choose. */
    val hasBook: Boolean = false,
    /** The active book this state was computed for; null without a book. */
    val bookId: String? = null,
    /** The figure being grown; null = the learner has to choose one. */
    val current: Figure? = null,
    val kind: FigureKind? = null,
    val collection: List<Figure> = emptyList(),
    val totalPoints: Int = 0,
    /** Learned words of the whole active book (the number the stage rules use), not of the selection. */
    val learnedWords: Int = 0,
    /** What the next stage still needs; null when the figure is fully grown. */
    val next: NextStage? = null,
    /** Progress of the points from the current to the next stage, 0..1 (both bars). */
    val pointsFraction: Float = 0f,
    /** What the vocabulary can reach today (locked stages). */
    val reach: Reach = Reach.EMPTY,
    /** A new avatar can be chosen (see [AvatarPlan.canStart]); false = paused until new words are imported. */
    val canStartNext: Boolean = true,
    /** The avatar being grown or chosen next in the current start of the book (see [AvatarPlan]). */
    val avatarNumber: Int = 1
) {
    /** The next stage cannot be reached today. */
    val nextLocked: Boolean get() = next?.let { reach.stageLocked(it.stage) } == true

    /** The bar is full, but sure words are still missing: a lock at its end (see [NextStage.onlySureWordsMissing]). */
    val waitingForSureWords: Boolean get() = next?.onlySureWordsMissing == true

    val needsChoice: Boolean get() = loaded && hasBook && current == null && canStartNext

    /** Every word of the book is used up by finished avatars: no choice, a short line instead. */
    val paused: Boolean get() = loaded && hasBook && current == null && !canStartNext

    /** The avatar for the start screen: the growing one, or the last finished one while [paused]. */
    val shown: Figure? get() = current ?: if (paused) collection.lastOrNull() else null
    val shownKind: FigureKind? get() = shown?.figureKind

    /**
     * After a finished figure the choice reads "Wähle deinen nächsten Avatar"; after "Buch neu
     * beginnen" it is avatar 1 again ("Wähle deinen Avatar"), even with a collection.
     */
    val choosingNext: Boolean get() = avatarNumber > 1
}

/** "Meine Abzeichen": one row per series (see [BadgeRules.progress]). */
data class BadgeState(val loaded: Boolean = false, val rows: List<BadgeProgress> = emptyList())

sealed interface QuizRoundState {
    data class Asking(
        val question: RoundQuestion,
        val totalWords: Int,
        /** Position on screen of the option tapped last; null while answering. */
        val selectedIndex: Int? = null,
        val phase: AnswerPhase = AnswerPhase.ANSWERING,
        /** Screen position → index in the question's options; reshuffled for a retry. */
        val order: List<Int> = question.question.options.indices.toList(),
        /** Set for a typing question; [phase] applies only after "Mit Vorschlägen" ([TypingPhase.CHOICE]). */
        val typing: TypingState? = question.question.typing?.let { TypingState() },
        /** A vocabulary question: the edit button may be shown (if switched on in the settings). */
        val editable: Boolean = false,
        /** A question with a [MultipleChoiceQuestion.secondStep] is in that step (past participle). */
        val inSecondStep: Boolean = false,
        /** Two-step question: the first answer of the first step was right; null until it is given. */
        val firstStepCorrect: Boolean? = null,
        /** Second step right at the first try, but the first step was not: the verb does not rise. */
        val firstStepMissed: Boolean = false
    ) : QuizRoundState {
        private val secondStep: AnswerStep? get() = question.question.secondStep?.takeIf { inSecondStep }

        /** The options of the current step in question order. */
        private val stepOptions: List<String> get() = secondStep?.options ?: question.question.options

        val options: List<String> get() = order.map { stepOptions[it] }

        /** Index of the correct option of the current step in question order. */
        val stepCorrectIndex: Int get() = secondStep?.correctIndex ?: question.question.correctIndex

        /** Screen position of the correct option. */
        val correctIndex: Int get() = order.indexOf(stepCorrectIndex)

        /** Shown after a wrong answer in the current step. */
        val solution: String get() = secondStep?.solution ?: question.question.solution

        /** Spoken after the answer of the current step. */
        val speakAfterAnswer: String? get() = secondStep?.speakAfterAnswer ?: question.question.speakAfterAnswer.takeIf { !inSecondStep }

        /** The first step of a two-step question: its answer is not the end of the question. */
        val isFirstOfTwoSteps: Boolean get() = question.question.secondStep != null && !inSecondStep
    }

    data class Finished(
        val correctOnFirstAttempt: Int,
        val totalWords: Int,
        /** Sum of the first-attempt credits in thirds ([RoundStars]); the stars are computed from it. */
        val starCredit: Int,
        /** First attempts that earned only one third. */
        val reducedCredits: Int,
        val wrongWords: List<QuizWord>,
        val mode: QuizMode,
        /** "Tagesziel geschafft! …" when this round reached today's goal (with the daily bonus). */
        val celebration: String? = null,
        /** Rise points of this round; null until all answers of the round are stored. */
        val roundPoints: Int? = null,
        /**
         * The words of "Details": every first attempt of the round; null until all answers of the round
         * are stored. The numbers of risen and dropped words are counted from it ([RoundReport.risen]).
         */
        val details: List<RoundDetail>? = null,
        /** The figure reached a new stage in this round (the highest one if several). */
        val figureGrowth: FigureGrowth? = null,
        /** Badge tiers earned in this round (vocabulary rounds only). */
        val newBadges: List<BadgeKey> = emptyList()
    ) : QuizRoundState
}

/** The irregular verbs the verb quiz knows with [forms]: "3 Formen" leaves out verbs without a past participle. */
internal fun verbsFor(forms: VerbFormMode, verbs: List<QuizWord>): List<QuizWord> =
    if (forms == VerbFormMode.TWO) verbs else verbs.filter { it.verbForms?.pastParticiple != null }

internal fun IrregularVerb.toQuizWord() = QuizWord(
    id = id,
    english = infinitive,
    germanMeanings = germanMeanings,
    unit = unit,
    part = part,
    leitnerLevel = leitnerLevel,
    page = page,
    storedType = WordType.VERB,
    verbForms = VerbForms(simplePast, pastParticiple),
    lastReviewedAt = lastReviewedAt
)

/** Everything that differs between the vocabulary quiz and the verb quiz. */
private class QuizSource(
    val mode: QuizMode,
    /** Non-deleted items in insertion (textbook) order; null until loaded. */
    val words: StateFlow<List<QuizWord>?>,
    /** Restored from the last session; invalid parts/pages are dropped by `normalize`. */
    val selection: MutableStateFlow<QuizFilter>,
    /** The generator for a new round; the verbs follow "Verbformen". */
    private val generatorNow: () -> QuestionGenerator,
    val recordFirstAttempt: suspend (
        id: String, correct: Boolean, early: Boolean, date: String, deviceId: String,
        form: AnswerForm, kept: KeptAnswer?
    ) -> FirstAttemptResult
) {
    val generator: QuestionGenerator get() = generatorNow()
}

class QuizViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabularyRepository(VocTrainerDatabase.getInstance(application), ExampleBook.loader(application))
    private val settings = AppSettings.getInstance(application)
    private val random: Random = Random.Default

    private val vocabularyGenerator = MultipleChoiceGenerator(random)

    private val vocabulary = QuizSource(
        mode = QuizMode.VOCABULARY,
        // The words stay null (no round can start) until every active entry has a core word.
        words = repository.vocabularyInInsertionOrder
            .onStart { ensureCoreWordsOnStart() }
            .map { list -> list.map { it.toQuizWord() } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null),
        selection = MutableStateFlow(QuizFilter()),
        generatorNow = { vocabularyGenerator },
        recordFirstAttempt = { id, correct, early, date, deviceId, form, kept ->
            repository.recordFirstAttempt(id, correct, early, date, deviceId, form, kept)
        }
    )

    private val twoFormGenerator = VerbDistractorGenerator(random)
    private val threeFormGenerator = VerbDistractorGenerator(random, VerbQuestion.THREE_FORMS)

    private val verbs = QuizSource(
        mode = QuizMode.VERBS,
        // With "3 Formen" verbs without a past participle are left out everywhere (selection, counts, rounds).
        words = combine(repository.verbsInInsertionOrder, settings.verbFormMode) { list, forms ->
            verbsFor(forms, list.map { it.toQuizWord() })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null),
        selection = MutableStateFlow(QuizFilter()),
        generatorNow = {
            if (settings.verbFormMode.value == VerbFormMode.THREE) threeFormGenerator else twoFormGenerator
        },
        // The verb quiz has no typing questions.
        recordFirstAttempt = { id, correct, early, date, deviceId, _, _ ->
            repository.recordVerbFirstAttempt(id, correct, early, date, deviceId)
        }
    )

    private val currentSource: QuizSource
        get() = if (settings.quizMode.value == QuizMode.VERBS) verbs else vocabulary

    /** The "one tip per visit of the start screen" rule; here, so an activity recreation keeps it. */
    val startVisit = StartVisit()

    private val _filtersExpanded = MutableStateFlow(false)
    val filtersExpanded: StateFlow<Boolean> = _filtersExpanded.asStateFlow()

    /** The selection when the selection screen was opened; null while it is closed. */
    private var selectionSnapshot: QuizFilter? = null

    private val _confirmLeaveSelection = MutableStateFlow(false)

    /** "Zurück" with a changed selection: ask whether to keep it. */
    val confirmLeaveSelection: StateFlow<Boolean> = _confirmLeaveSelection.asStateFlow()

    val vibration: StateFlow<Boolean> = settings.vibration

    /** The selection tip was completed with "Verstanden" (the trigger of the tip "Los geht's"). */
    val selectionTipDone: StateFlow<Boolean> = settings.tips
        .map { Tip.SELECTION in it.done }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Tip.SELECTION in settings.tips.value.done)

    /** "Das hast du geschafft" (N3) is done: the trigger of "Dein Lernstand". */
    val roundResultTipDone: StateFlow<Boolean> = settings.tips
        .map { Tip.ROUND_RESULT in it.done }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Tip.ROUND_RESULT in settings.tips.value.done)

    /** "Dein Lernstand" (Npre4) is done: the trigger of the avatar tip. */
    val levelsTipDone: StateFlow<Boolean> = settings.tips
        .map { Tip.LEVELS in it.done }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Tip.LEVELS in settings.tips.value.done)

    /** Test app only: "Richtige Antwort markieren"; always false in release builds. */
    val markCorrectAnswer: StateFlow<Boolean> = settings.markCorrectAnswer

    // Looked up on every use: the app-wide instance is released when the activity finishes.
    private val speaker: Speaker get() = Speaker.getInstance(getApplication())

    /** The speaker button is shown only if an English voice is available. */
    val speechAvailable: StateFlow<Boolean> = speaker.state
        .map { it == SpeechState.AVAILABLE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _progressExpanded = MutableStateFlow(false)

    /** Details of the "Dein Lernstand" card; not persisted. */
    val progressExpanded: StateFlow<Boolean> = _progressExpanded.asStateFlow()

    /**
     * The current day; refreshed when the app comes to the foreground ([refreshDay]) and when the
     * test app shifts the date.
     */
    private val today = MutableStateFlow(AppClock.today())

    /** Test app only: the simulated date while "Datum verschieben" is active; null = real date. */
    val simulatedDate: StateFlow<String?> = combine(AppClock.offsetDays, today) { offset, day ->
        if (offset == 0) null else TestToolsTexts.simulatedDateMarker(day)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The book whose saved selections are in [vocabulary] and [verbs]; null until they are loaded. */
    private class SelectionsOf(val bookId: String?)

    private val selectionsOf = MutableStateFlow<SelectionsOf?>(null)

    /** Title of the active book, small in the top bar of the start screen; null without a book. */
    val activeBookTitle: StateFlow<String?> = repository.activeBook.map { it?.title }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private class Current(val mode: QuizMode, val list: List<QuizWord>?, val chosen: QuizFilter)

    // The list counts as not loaded until the saved selections of the book are, so the start
    // screen never shows the default selection first.
    private val current = combine(
        settings.quizMode, vocabulary.words, vocabulary.selection, verbs.words,
        combine(verbs.selection, selectionsOf) { selection, loaded -> selection to (loaded != null) }
    ) { mode, words, wordSelection, verbList, (verbSelection, loaded) ->
        when {
            !loaded -> Current(mode, null, if (mode == QuizMode.VERBS) verbSelection else wordSelection)
            mode == QuizMode.VERBS -> Current(mode, verbList, verbSelection)
            else -> Current(mode, words, wordSelection)
        }
    }

    private val roundTick = MutableStateFlow(0)

    /**
     * The round the next start uses, picked ahead of time; made again when the words, the selection,
     * the mode or the day change and after each start ([roundTick]).
     */
    val nextRound: StateFlow<NextRoundPreview?> = combine(current, today, roundTick, settings.verbFormMode) { current, day, _, _ ->
        current to day
    }
        .map { (current, day) ->
            val list = current.list ?: return@map null
            val filter = QuizSelection.normalize(list, current.chosen)
            val generator = if (current.mode == QuizMode.VERBS) verbs.generator else vocabulary.generator
            val askable = QuizSelection.filter(list, filter).filter { generator.canGenerate(it, list) }
            val zone = ZoneId.systemDefault()
            NextRoundPreview(current.mode, list, filter, day, QuizRound.pickWords(askable, random, day, zone), zone)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** See [FirstRound.done]; null while the vocabulary is loading. */
    private val firstRoundDone = combine(vocabulary.words, repository.practiceDays) { words, days ->
        words?.let { FirstRound.done(it, days.map { day -> day.roundsCompleted }) }
    }

    /** Verbs of the active book exist, with or without a past participle. */
    private val anyVerbs = repository.verbsInInsertionOrder.map { it.isNotEmpty() }

    val startState: StateFlow<QuizStartState> = combine(
        current, today, nextRound, firstRoundDone, combine(anyVerbs, settings.verbFormMode) { exist, forms -> exist to forms }
    ) { current, day, preview, started, (verbsExist, forms) ->
        val mode = current.mode
        val list = current.list ?: return@combine QuizStartState(mode = mode)
        val filter = QuizSelection.normalize(list, current.chosen)
        val selected = QuizSelection.filter(list, filter)
        QuizStartState(
            loaded = true,
            mode = mode,
            unitChoices = QuizSelection.unitChoices(list),
            partOptions = QuizSelection.partOptions(list, filter.unit),
            pages = QuizSelection.availablePages(list, filter),
            filter = filter,
            wordCount = selected.size,
            progress = LearningProgress.of(selected),
            tooFewWords = mode == QuizMode.VOCABULARY && list.size < MIN_VOCABULARY,
            noVerbs = mode == QuizMode.VERBS && list.isEmpty() && !verbsExist,
            noThreeFormVerbs = mode == QuizMode.VERBS && list.isEmpty() && verbsExist,
            threeVerbForms = forms == VerbFormMode.THREE,
            hasTypeable = mode == QuizMode.VOCABULARY && selected.any { it.isTypeable },
            nextRoundLine = preview?.takeIf { it.isFor(mode, list, filter, day) }
                ?.let { nextRoundText(it.learnable, it.newCount, mode) },
            firstRoundDone = started ?: true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QuizStartState())

    /** Daily goal and streak over all devices. */
    val streak: StateFlow<StreakInfo?> = combine(repository.practiceDays, settings.dailyGoal, today) { days, goal, day ->
        Streak.compute(days.map { it.toRecord() }, day, settings.deviceId, goal)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _startScreenRequested = MutableStateFlow(false)

    /** Set when the reminder notification was tapped; the app then shows the quiz screen. */
    val startScreenRequested: StateFlow<Boolean> = _startScreenRequested.asStateFlow()

    fun requestStartScreen() {
        _startScreenRequested.value = true
    }

    fun consumeStartScreenRequest() {
        _startScreenRequested.value = false
    }

    /** Called when the app comes to the foreground, so "today" is right after midnight. */
    fun refreshDay() {
        today.value = AppClock.today()
    }

    private val _round = MutableStateFlow<QuizRoundState?>(null)

    /** null = start screen. */
    val round: StateFlow<QuizRoundState?> = _round.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var activeRound: QuizRound? = null
    private var activeSource: QuizSource? = null
    private var autoAdvance: Job? = null
    private var choiceDelay: Job? = null

    /** "Auch richtig" lookup over all vocabulary, built at the start of a vocabulary round. */
    private var alsoCorrectIndex = AlsoCorrectIndex(emptyList())

    /** All words of the learning material ("Fast richtig"), built together with [alsoCorrectIndex]. */
    private var learningMaterial = LearningMaterial.EMPTY

    /** Points of one round; the result screen waits for its stored first attempts. */
    private class RoundTally {
        val records = mutableListOf<Job>()
        val attempts = mutableListOf<AttemptRecord>()
        var points = 0
    }

    private var tally = RoundTally()

    /** Test app only ("Tipp sofort zeigen"): the running round is a demo; none of its answers is stored. */
    private var demoRound = false

    init {
        pointsIntroOnce()
        viewModelScope.launch { AppClock.offsetDays.collect { today.value = AppClock.today() } }
        // Each book keeps its own selections: loaded when the active book changes (not when the
        // same book is saved again, which would drop a draft).
        viewModelScope.launch {
            repository.activeBook.collect { book ->
                if (selectionsOf.value?.bookId == book?.id && selectionsOf.value != null) return@collect
                vocabulary.selection.value = book?.quizFilter(QuizMode.VOCABULARY) ?: QuizFilter()
                verbs.selection.value = book?.quizFilter(QuizMode.VERBS) ?: QuizFilter()
                selectionsOf.value = SelectionsOf(book?.id)
            }
        }
        // "Sicherung wiederherstellen" may bring other saved selections for the same book.
        viewModelScope.launch {
            settings.restoreCount.drop(1).collect {
                val book = repository.activeBookNow()
                vocabulary.selection.value = book?.quizFilter(QuizMode.VOCABULARY) ?: QuizFilter()
                verbs.selection.value = book?.quizFilter(QuizMode.VERBS) ?: QuizFilter()
                selectionsOf.value = SelectionsOf(book?.id)
            }
        }
    }

    val figureState: StateFlow<FigureState> = combine(
        repository.figures, repository.totalPoints, vocabulary.words, repository.activeBook
    ) { figures, total, words, book ->
        if (words == null) FigureState()
        else figureStateOf(figures, total, words, hasBook = book != null, slotStart = book?.figureSlotStart ?: 0).copy(bookId = book?.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FigureState())

    /** The badge rows of "Mein Avatar": earned badges and the current values. */
    val badgeState: StateFlow<BadgeState> = combine(
        repository.badges, repository.vocabularyInInsertionOrder, streak
    ) { badges, words, streak ->
        if (streak == null) return@combine BadgeState()
        BadgeState(true, BadgeRules.progress(badgeValues(words, streak.longest), badges.keys()))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BadgeState())

    /** Final choice of the figure to grow; the stage is computed at once from the existing points. */
    fun chooseFigure(kind: FigureKind) {
        viewModelScope.launch { repository.chooseFigure(kind) }
    }

    /**
     * The start screen shows the figure in [slot] at [stage]: true if it should wiggle (see
     * [FigureWiggle]); the stage is stored as shown either way.
     */
    fun figureShownOnStart(bookId: String, slot: Int, stage: Int): Boolean {
        val wiggle = FigureWiggle.shouldWiggle(settings.lastShownFigureStage(bookId, slot), stage)
        settings.setLastShownFigureStage(bookId, slot, stage)
        return wiggle
    }

    /** Recomputes the stage and awards badges without celebrating (start screen, opening "Mein Avatar"). */
    fun refreshFigure() {
        viewModelScope.launch {
            repository.updateFigureStage()
            repository.checkBadges(settings)
        }
    }

    /** Test app only: "🧪 +100 Punkte". */
    fun addTestPoints() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            repository.addTestPoints(AppClock.today().toString(), settings.deviceId, TEST_POINTS)
            repository.updateFigureStage()
        }
    }

    /**
     * Test app only ("Tipp sofort zeigen"): shows [tip] in its real place with the example book. The
     * example book becomes the active book in the vocabulary mode;
     * "Selbst tippen" starts a demo round of its typeable words at the typing level and "Das hast du
     * geschafft" shows the result of an invented round. Demo rounds change levels in memory only and
     * store nothing (no level, points, streak or practice day); the other tips come on the start screen.
     */
    fun showTipDemo(tip: Tip) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            repository.setActiveBook(ExampleBook.ID)
            settings.setQuizMode(QuizMode.VOCABULARY)
            backToStart()
            _progressExpanded.value = false
            startVisit.end()
            if (tip == Tip.VERBS && repository.verbsInInsertionOrder.first().isEmpty()) {
                _message.value = TestToolsTexts.NO_VERBS_FOR_TIP
                return@launch
            }
            // Ignores "done" and the limit of one tip per visit; the chained tip follows after "Verstanden".
            settings.showTipNow(tip)
            startVisit.force(tip)
            Tip.CHAIN[tip]?.let(startVisit::force)
            val pool = repository.vocabularyInInsertionOrder.first().filterNot { it.isDeleted }.map { it.toQuizWord() }
            when (tip) {
                Tip.VERBS -> settings.setQuizMode(QuizMode.VERBS)
                Tip.TYPING -> startTypingDemo(pool)
                Tip.REPEAT -> startRepeatDemo(pool)
                Tip.ROUND_RESULT -> showResultDemo(pool)
                else -> Unit
            }
        }
    }

    /** A demo round whose first shown question is a repetition: its words were "answered wrongly" before. */
    private suspend fun startRepeatDemo(pool: List<QuizWord>) {
        val words = pool.take(REPEAT_DEMO_WORDS)
        if (words.isEmpty()) return
        buildTypedLookups(pool)
        activeSource = vocabulary
        tally = RoundTally()
        demoRound = true
        activeRound = QuizRound(words, pool, vocabulary.generator, emptySet()).apply {
            repeat(words.size) { if (nextQuestion() != null) recordAnswer(correct = false) }
        }
        showNextQuestion()
    }

    private suspend fun startTypingDemo(pool: List<QuizWord>) {
        val words = pool.filter { it.isTypeable }.take(QuizRound.ROUND_SIZE)
            .map { it.copy(leitnerLevel = QuestionDirection.TYPING_FROM_LEVEL) }
        if (words.isEmpty()) return
        buildTypedLookups(pool)
        activeSource = vocabulary
        tally = RoundTally()
        demoRound = true
        activeRound = QuizRound(words, pool, vocabulary.generator, emptySet())
        showNextQuestion()
    }

    /** Every word of the invented round rose by one level and brought its points. */
    private fun showResultDemo(pool: List<QuizWord>) {
        val words = pool.take(QuizRound.ROUND_SIZE)
        if (words.isEmpty()) return
        val records = words.map { word ->
            val level = word.leitnerLevel.coerceAtMost(Leitner.MAX_LEVEL - 1)
            AttemptRecord(word.english, LevelOutcome(level, level + 1, LevelReason.RISE), Points.forNewLevels(level, level + 1))
        }
        _round.value = QuizRoundState.Finished(
            correctOnFirstAttempt = words.size,
            totalWords = words.size,
            starCredit = words.size * RoundStars.FULL_CREDIT,
            reducedCredits = 0,
            wrongWords = emptyList(),
            mode = QuizMode.VOCABULARY,
            roundPoints = records.sumOf { it.points },
            details = RoundReport.details(records)
        )
    }

    /** Start-up work once the database is available: the level-cap notice off, the figure stage, the badges. */
    private fun pointsIntroOnce() {
        viewModelScope.launch {
            // The level-3 notice of the backup settings is never shown: mark it done and clear it.
            if (!settings.levelCapIntroDone) settings.finishLevelCapIntro(0)
            settings.dismissLevelCapNotice()
            // The figure may have grown elsewhere.
            repository.updateFigureStage()
            // Awards earlier progress retroactively, without a notice.
            repository.checkBadges(settings)
        }
    }

    /**
     * Silent start-up run of [VocabularyRepository.ensureExampleBook] and
     * [VocabularyRepository.ensureCoreWords]; only logged.
     */
    private suspend fun ensureCoreWordsOnStart() {
        try {
            repository.ensureExampleBook()?.let { import ->
                val counts = import.counts
                if (import.isNewBook || counts.vocabularyImported > 0 || counts.verbsAdded > 0) {
                    Log.i(
                        LOG_TAG,
                        "Example book: ${counts.vocabularyImported} entries, ${counts.verbsAdded} verbs added, active=${import.isActive}"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(LOG_TAG, "Example book could not be applied", e)
        }
        val ignored = repository.ensureCoreWords()
        if (ignored > 0) Log.i(LOG_TAG, "$ignored vocabulary entries without core word ignored at start")
    }

    fun selectMode(mode: QuizMode) {
        _message.value = null
        settings.setQuizMode(mode)
    }

    fun selectUnit(unit: UnitChoice) = updateFilter { QuizSelection.withUnit(it, unit) }

    fun togglePart(part: String?) = updateFilter { QuizSelection.togglePart(it, part, startState.value.partOptions) }

    fun selectAllParts() = updateFilter(QuizSelection::withAllParts)

    fun selectPageFrom(page: Int?) = updateFilter { QuizSelection.withPageFrom(it, page) }

    fun selectPageTo(page: Int?) = updateFilter { it.copy(pageTo = page) }

    // Builds on the normalized filter so stale selections never come back. Changes the draft only;
    // "Auswahl übernehmen" saves it.
    private fun updateFilter(change: (QuizFilter) -> QuizFilter) {
        _message.value = null
        val source = currentSource
        val current = source.words.value?.let { QuizSelection.normalize(it, source.selection.value) }
            ?: source.selection.value
        source.selection.value = change(current)
    }

    fun toggleProgress() {
        _progressExpanded.value = !_progressExpanded.value
    }

    /** Opens the selection screen; its changes are a draft until "Auswahl übernehmen". */
    fun openSelection() {
        // Opening and closing the selection screen starts a new visit (tip "Auswahl", then "Los geht's").
        startVisit.end()
        val source = currentSource
        selectionSnapshot = source.words.value?.let { QuizSelection.normalize(it, source.selection.value) }
            ?: source.selection.value
        _filtersExpanded.value = true
    }

    /** "Zurück" and the system back: leaves at once without changes, otherwise asks first. */
    fun leaveSelection() {
        if (selectionChanged()) _confirmLeaveSelection.value = true else closeSelection()
    }

    /** "Auswahl übernehmen" (also in the dialog): saves the draft and leaves. */
    fun applySelection() {
        val source = currentSource
        val filter = source.selection.value
        viewModelScope.launch { repository.saveQuizFilter(source.mode, filter) }
        settings.markSelectionApplied()
        closeSelection()
    }

    /** "Verwerfen": back to the selection from when the screen was opened. */
    fun discardSelection() {
        selectionSnapshot?.let { currentSource.selection.value = it }
        closeSelection()
    }

    /** The dialog was dismissed (tap outside, back): the learner stays on the selection screen. */
    fun dismissLeaveSelection() {
        _confirmLeaveSelection.value = false
    }

    private fun selectionChanged(): Boolean {
        val snapshot = selectionSnapshot ?: return false
        val source = currentSource
        val words = source.words.value ?: return snapshot != source.selection.value
        return QuizSelection.isChanged(words, snapshot, source.selection.value)
    }

    private fun closeSelection() {
        startVisit.end()
        _message.value = null
        _confirmLeaveSelection.value = false
        selectionSnapshot = null
        _filtersExpanded.value = false
    }

    fun startRound() {
        val source = currentSource
        val pool = source.words.value ?: return
        val filter = startState.value.filter
        val day = AppClock.today()
        // Exactly the previewed round, unless its inputs have changed since.
        val preview = nextRound.value?.takeIf { it.isFor(source.mode, pool, filter, day) }
        roundTick.value++
        viewModelScope.launch {
            val picked = withContext(Dispatchers.Default) {
                val pick = preview?.pick ?: run {
                    val askable = QuizSelection.filter(pool, filter)
                        .filter { source.generator.canGenerate(it, pool) }
                    QuizRound.pickWords(askable, random, day, ZoneId.systemDefault())
                }
                val irregular = pick.words
                if (source.mode != QuizMode.VERBS || irregular.isEmpty()) return@withContext pick
                // Regular verbs from the vocabulary of the same selection, so "-ed" is not always wrong.
                val vocabularyOfSelection = QuizSelection.filter(vocabulary.words.value.orEmpty(), filter)
                val regular = RegularVerbs.pick(
                    RegularVerbs.detect(vocabularyOfSelection, pool), pool, source.generator, random
                )
                pick.copy(words = RegularVerbs.mixIn(irregular, regular, random))
            }
            if (source.mode == QuizMode.VOCABULARY) buildTypedLookups(pool)
            if (picked.words.isEmpty()) {
                _round.value = null
                _message.value = "Für diese Auswahl können keine Fragen erstellt werden."
                return@launch
            }
            _message.value = null
            settings.roundStarted()
            startVisit.end()
            _filtersExpanded.value = false
            _progressExpanded.value = false
            activeSource = source
            tally = RoundTally()
            demoRound = false
            activeRound = QuizRound(picked.words, pool, source.generator, picked.earlyIds)
            showNextQuestion()
        }
    }

    /** "Auch richtig" index and learning material from all vocabulary [pool] and all irregular verbs. */
    private suspend fun buildTypedLookups(pool: List<QuizWord>) {
        val verbList = verbs.words.value.orEmpty()
        withContext(Dispatchers.Default) {
            alsoCorrectIndex = AlsoCorrectIndex(pool)
            learningMaterial = LearningMaterial(pool, verbList)
        }
    }

    fun answer(optionIndex: Int) {
        val state = _round.value as? QuizRoundState.Asking ?: return
        val round = activeRound ?: return
        val source = activeSource ?: return
        val outcome = QuizInteraction.tap(state, optionIndex, round) as? TapOutcome.Answered ?: return
        applyAnswer(state, outcome, source)
    }

    /** Input field of a typing question. */
    fun typingInput(text: String) {
        val state = _round.value as? QuizRoundState.Asking ?: return
        _round.value = QuizInteraction.typingInput(state, text)
    }

    /** "Mit Vorschlägen": the options instead of typing. */
    fun chooseInstead() {
        val state = _round.value as? QuizRoundState.Asking ?: return
        choiceDelay?.cancel()
        _round.value = QuizInteraction.chooseInstead(state)
    }

    /** "Prüfen" or the keyboard action of a typing question. */
    fun submitTyped() {
        val state = _round.value as? QuizRoundState.Asking ?: return
        val round = activeRound ?: return
        val source = activeSource ?: return
        when (val outcome = QuizInteraction.submitTyped(state, round, alsoCorrectIndex, learningMaterial)) {
            is TapOutcome.Answered -> {
                choiceDelay?.cancel()
                applyAnswer(state, outcome, source)
            }
            is TapOutcome.Updated -> _round.value = outcome.state
            TapOutcome.Ignored -> Unit
        }
    }

    private fun applyAnswer(state: QuizRoundState.Asking, outcome: TapOutcome.Answered, source: QuizSource) {
        _round.value = outcome.state
        val speech = if (outcome.speaks) {
            QuizInteraction.autoSpeechAfterTap(state, settings.autoSpeak.value)?.let { speaker.speak(it) }
        } else {
            null
        }
        val word = state.question.question.word
        var recorded: Deferred<FirstAttemptResult>? = null
        if (outcome.countsForLevel && word.countsForProgress && !demoRound) {
            val early = state.question.pickedEarly
            val roundTally = tally
            val rewards = source.mode.countsForRewards
            recorded = viewModelScope.async {
                // Finish storing even if the round is left, so level and points stay together.
                val result = withContext(NonCancellable) {
                    source.recordFirstAttempt(
                        word.id, outcome.countsAsCorrect, early, AppClock.today().toString(), settings.deviceId,
                        outcome.form, outcome.kept
                    )
                }
                roundTally.points += result.points
                result.outcome?.let { levelOutcome ->
                    // The review date was just stored, so the pause counts from now (as the due date in the word details).
                    val availableFrom = if (levelOutcome.reason == LevelReason.STAYS_NOT_DUE) {
                        val zone = ZoneId.systemDefault()
                        Due.dueDate(levelOutcome.newLevel, AppClock.nowMillis(zone), zone)
                    } else {
                        null
                    }
                    val record = AttemptRecord(
                        word.english, levelOutcome, result.points, pointsApply = rewards,
                        availableFrom = availableFrom,
                        almost = outcome.state.typing?.almost?.kind
                    )
                    roundTally.attempts += record
                }
                result
            }
            roundTally.records += recorded
        }
        // After a wrong answer nothing moves on by itself; the learner continues with "Nochmal versuchen".
        if (outcome.advancesAutomatically) {
            // "Das Simple Past war noch nicht richtig." needs time to read, like "Auch richtig".
            val longPause = outcome.kept == KeptAnswer.ALSO_CORRECT || outcome.state.firstStepMissed
            val pause = if (longPause) ALSO_CORRECT_DELAY_MS else CORRECT_ANSWER_DELAY_MS
            autoAdvance = viewModelScope.launch {
                if (speech == null) {
                    delay(pause)
                } else {
                    // Let the spoken answer finish, but never wait longer than the limit.
                    withTimeoutOrNull(SPOKEN_ANSWER_MAX_DELAY_MS) {
                        joinAll(launch { delay(pause) }, launch { speech.await() })
                    }
                }
                // Two steps: after the simple past comes the past participle of the same verb.
                val next = (_round.value as? QuizRoundState.Asking)?.let(QuizInteraction::nextStep)
                if (next != null) _round.value = next else showNextQuestion()
            }
        }
    }

    /** Speaker button: the question before the answer, the solution once answered. */
    fun speak() {
        val state = _round.value as? QuizRoundState.Asking ?: return
        QuizInteraction.speakerText(state)?.let { speaker.speak(it) }
    }

    /** "Nochmal versuchen": the same options reshuffled, to find the correct one again. */
    fun retry() {
        val state = _round.value as? QuizRoundState.Asking ?: return
        val retry = QuizInteraction.retry(state, random)
        _round.value = retry
        if (retry !== state) QuizInteraction.autoSpeechOnRetry(retry, settings.autoSpeak.value)?.let { speaker.speak(it) }
    }

    /** Ends a running round early; answers given so far keep their (already saved) level changes. */
    fun cancelRound() = backToStart()

    /** Back to the start screen, which always shows the collapsed summary of the last selection. */
    fun backToStart() {
        autoAdvance?.cancel()
        choiceDelay?.cancel()
        speaker.stop()
        activeRound = null
        activeSource = null
        demoRound = false
        _filtersExpanded.value = false
        _round.value = null
        // A cancelled round never reaches recordFinishedRound: grow the figure and award badges
        // (silently) once its answers are stored.
        val roundTally = tally
        viewModelScope.launch {
            roundTally.records.toList().joinAll()
            repository.updateFigureStage()
            repository.checkBadges(settings)
        }
    }

    private fun showNextQuestion() {
        val round = activeRound ?: return
        val question = round.nextQuestion()
        choiceDelay?.cancel()
        if (question != null) {
            present(question, round, levelIsCurrent = false)
        } else {
            speaker.stop()
            activeRound = null
            val finished = QuizRoundState.Finished(
                round.correctOnFirstAttempt,
                round.size,
                round.starCredit,
                round.reducedCredits,
                round.wrongOnFirstAttempt,
                activeSource?.mode ?: QuizMode.VOCABULARY
            )
            // "Tipp sofort zeigen" (test app): nothing of the demo round is stored.
            if (demoRound) {
                backToStart()
                return
            }
            // Set the state first: recordFinishedRound only fills in the state that is still shown, and
            // its coroutine may run up to that check at once (all answers already stored, no suspension).
            _round.value = finished
            recordFinishedRound(finished)
        }
    }

    /**
     * Shows [question]. [levelIsCurrent]: the word was just re-read from the database (after
     * editing), so its level already includes the drop of a wrong first answer.
     */
    private fun present(question: RoundQuestion, round: QuizRound, levelIsCurrent: Boolean) {
        if (settings.autoSpeak.value) question.question.speakOnShow?.let { speaker.speak(it) }
        val editable = activeSource?.mode == QuizMode.VOCABULARY
        _round.value = QuizRoundState.Asking(question, round.size, editable = editable).also { asking ->
            if (asking.typing != null) startChoiceDelay(question, levelIsCurrent)
        }
    }

    /**
     * The edit button on a vocabulary question is shown (setting "Bearbeiten-Knopf in der Abfrage").
     * Test app (debug build) only; a stored value has no effect in the release app.
     */
    val editButton: StateFlow<Boolean> =
        if (BuildConfig.DEBUG) settings.editButtonInQuiz else MutableStateFlow(false)

    /** The word whose edit page is open; the question is rebuilt when the learner comes back. */
    private var editingWordId: String? = null

    /**
     * The edit button: pauses the round (no auto-advance, no "Mit Vorschlägen" timer, no speech) and
     * returns the id of the word to edit; null if there is nothing to edit. The round state is
     * kept in this ViewModel, so the round survives the edit page ([resumeAfterEdit]).
     */
    fun startEditingCurrentWord(): String? {
        val state = _round.value as? QuizRoundState.Asking ?: return null
        if (!state.editable || activeRound == null) return null
        autoAdvance?.cancel()
        choiceDelay?.cancel()
        speaker.stop()
        return state.question.question.word.id.also { editingWordId = it }
    }

    /**
     * Back from the edit page: rebuilds the question from the current data of the word (answer
     * so far discarded, "Mit Vorschlägen" delay starts again); a deleted word is skipped silently.
     */
    fun resumeAfterEdit() {
        val id = editingWordId ?: return
        editingWordId = null
        val round = activeRound ?: return
        viewModelScope.launch {
            val updated = repository.vocabularyById(id)?.takeIf { !it.isDeleted }?.toQuizWord()
            if (activeRound !== round) return@launch
            val result = round.reaskAfterEdit(updated)
            // The meanings and words of the edited entry may have changed.
            buildTypedLookups(round.pool)
            when (result) {
                is ReaskResult.Question -> present(result.question, round, levelIsCurrent = true)
                ReaskResult.Next -> showNextQuestion()
            }
        }
    }

    /** "Mit Vorschlägen" becomes possible once per question, [auswahlDelayMs] of the word's level after it appeared. */
    private fun startChoiceDelay(question: RoundQuestion, levelIsCurrent: Boolean) {
        choiceDelay = viewModelScope.launch {
            val level = question.question.word.leitnerLevel
            delay(auswahlDelayMs(if (levelIsCurrent) level else levelForAuswahlDelay(level, question.isRepeat)))
            val state = _round.value as? QuizRoundState.Asking ?: return@launch
            if (state.question === question) _round.value = QuizInteraction.choiceDelayPassed(state)
        }
    }

    /**
     * Vocabulary rounds only: counts the round for today's goal; adds the round's points, the daily bonus and the
     * celebration if this round reached the goal, the figure growth and the badges earned in this round.
     */
    private fun recordFinishedRound(finished: QuizRoundState.Finished) {
        val roundTally = tally
        viewModelScope.launch {
            roundTally.records.toList().joinAll()
            if (!finished.mode.countsForRewards) {
                // Verb training: no daily goal, streak, points, bonus or figure; only the level changes.
                if (_round.value === finished) {
                    _round.value = finished.copy(details = RoundReport.risesFirst(RoundReport.details(roundTally.attempts)))
                }
                return@launch
            }
            val day = AppClock.today()
            today.value = day
            val goal = settings.dailyGoal.value
            suspend fun streakNow() =
                Streak.compute(repository.allPracticeDays().map { it.toRecord() }, day, settings.deviceId, goal)
            val before = streakNow()
            repository.recordRoundCompleted(day.toString(), settings.deviceId, goal)
            val after = streakNow()
            val celebration = if (!before.todayReached && after.todayReached) {
                val bonus = repository.recordDailyBonus(day.toString(), settings.deviceId, Points.dailyBonus(after.current))
                PracticeTexts.celebration(after.current, bonus)
            } else {
                null
            }
            val growth = repository.updateFigureStage()
            val newBadges = repository.checkBadges(settings, longestStreak = after.longest)
            if (_round.value === finished) {
                _round.value = finished.copy(
                    celebration = celebration,
                    roundPoints = roundTally.points,
                    details = RoundReport.details(roundTally.attempts),
                    figureGrowth = growth,
                    newBadges = newBadges
                )
            }
        }
    }
}

/**
 * The figure of the active book with [total] points and its [words]: all active words of the
 * book, never only the quiz selection, so the goal of the next stage does not depend on it.
 */
internal fun figureStateOf(
    figures: List<Figure>, total: Int, words: List<QuizWord>, hasBook: Boolean = true, slotStart: Int = 0
): FigureState {
    val current = figures.current(slotStart)
    val counts = FigureRules.countWords(words)
    val plan = figures.avatarPlan(words.size, slotStart)
    val since = current?.let { FigureRules.pointsSinceStart(total, it.pointsAtStart) } ?: 0
    val next = current?.let { FigureRules.nextStage(it.stage, since, counts, plan.number) }
    return FigureState(
        loaded = true,
        hasBook = hasBook,
        current = current,
        kind = current?.figureKind,
        collection = figures.collection(),
        totalPoints = total,
        learnedWords = counts.sure,
        next = next,
        pointsFraction = current?.let { FigureRules.pointsProgress(it.stage, since, plan) } ?: 1f,
        reach = Reachability.of(words, plan.number),
        avatarNumber = plan.number,
        canStartNext = plan.canStart
    )
}

/**
 * The level that decides the "Mit Vorschlägen" delay: the level of the word when the question is
 * shown. A repetition only follows a wrong first answer, which has already dropped the word by one.
 */
fun levelForAuswahlDelay(startLevel: Int, isRepeat: Boolean): Int =
    if (isRepeat) (startLevel - 1).coerceAtLeast(0) else startLevel
