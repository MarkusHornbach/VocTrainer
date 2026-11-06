package io.github.markushornbach.voctrainer.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.AlmostKind
import io.github.markushornbach.voctrainer.learning.GermanText
import io.github.markushornbach.voctrainer.learning.QuestionDirection
import io.github.markushornbach.voctrainer.learning.Reachability
import io.github.markushornbach.voctrainer.learning.RoundStars
import io.github.markushornbach.voctrainer.learning.StreakInfo
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.markushornbach.voctrainer.ui.theme.LocalAnswerColors
import io.github.markushornbach.voctrainer.ui.theme.LocalLevelKindColors
import io.github.markushornbach.voctrainer.ui.theme.LocalGoalFrameColor
import io.github.markushornbach.voctrainer.ui.theme.KindColor
import io.github.markushornbach.voctrainer.ui.theme.TestOrange
import io.github.markushornbach.voctrainer.learning.LearningProgress
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.unit.sp

@Composable
fun QuizScreen(
    modifier: Modifier = Modifier,
    viewModel: QuizViewModel = viewModel(),
    onOpenFigure: () -> Unit = {},
    /** Opens the edit page of the vocabulary with this id (the edit button of a question). */
    onEditWord: (String) -> Unit = {}
) {
    val round by viewModel.round.collectAsState()
    val contentModifier = modifier
        .fillMaxSize()
        .padding(16.dp)

    when (val state = round) {
        null -> {
            val start by viewModel.startState.collectAsState()
            val message by viewModel.message.collectAsState()
            val expanded by viewModel.filtersExpanded.collectAsState()
            val progressExpanded by viewModel.progressExpanded.collectAsState()
            val streak by viewModel.streak.collectAsState()
            val confirmLeave by viewModel.confirmLeaveSelection.collectAsState()
            if (confirmLeave) {
                LeaveSelectionDialog(
                    onApply = viewModel::applySelection,
                    onDiscard = viewModel::discardSelection,
                    onDismiss = viewModel::dismissLeaveSelection
                )
            }
            val figure by viewModel.figureState.collectAsState()
            val selectionTipDone by viewModel.selectionTipDone.collectAsState()
            val roundResultTipDone by viewModel.roundResultTipDone.collectAsState()
            val levelsTipDone by viewModel.levelsTipDone.collectAsState()
            // Like "Mein Avatar": a stage reached outside a finished round (e.g. a cancelled
            // round) shows here too, so the bar starts from the right stage.
            LaunchedEffect(Unit) { viewModel.refreshFigure() }
            // Verb training gives no points, so the figure (and its compulsory choice) waits for the vocabulary mode.
            if (showsFigure(start.mode) && figure.needsChoice) {
                FigureChoice(FigureTexts.choiceTitle(figure.choosingNext), onChoose = viewModel::chooseFigure, modifier = contentModifier)
                return
            }
            QuizStart(
                state = start,
                streak = streak,
                figure = figure,
                tipTriggers = startTipTriggers(
                    figure, selectionTipDone, roundResultTipDone, levelsTipDone, start.mode,
                    verbsShown = start.loaded && !start.noVerbs
                ),
                onOpenFigure = onOpenFigure,
                onFigureShown = viewModel::figureShownOnStart,
                message = message,
                expanded = expanded,
                progressExpanded = progressExpanded,
                actions = StartActions(
                    onSelectMode = viewModel::selectMode,
                    onSelectUnit = viewModel::selectUnit,
                    onTogglePart = viewModel::togglePart,
                    onSelectAllParts = viewModel::selectAllParts,
                    onSelectPageFrom = viewModel::selectPageFrom,
                    onSelectPageTo = viewModel::selectPageTo,
                    onOpenSelection = viewModel::openSelection,
                    onLeaveSelection = viewModel::leaveSelection,
                    onApplySelection = viewModel::applySelection,
                    onToggleProgress = viewModel::toggleProgress,
                    onStart = viewModel::startRound
                ),
                modifier = contentModifier
            )
        }
        is QuizRoundState.Asking -> {
            val vibration by viewModel.vibration.collectAsState()
            val speechAvailable by viewModel.speechAvailable.collectAsState()
            val markCorrect by viewModel.markCorrectAnswer.collectAsState()
            val editButton by viewModel.editButton.collectAsState()
            var confirmCancel by rememberSaveable { mutableStateOf(false) }
            // The tips of a vocabulary question may come; the dialog "Runde abbrechen?" blocks every tip.
            BlockTips(allow = if (confirmCancel) emptySet() else Tip.IN_QUESTION)
            BackHandler { confirmCancel = true }
            if (confirmCancel) {
                AlertDialog(
                    onDismissRequest = { confirmCancel = false },
                    title = { Text("Runde abbrechen?") },
                    text = { Text("Bisherige Antworten bleiben gespeichert.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmCancel = false
                            viewModel.cancelRound()
                        }) { Text("Abbrechen") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCancel = false }) { Text("Weiterspielen") }
                    }
                )
            }
            QuizQuestion(
                state = state,
                vibration = vibration,
                markCorrect = markCorrect,
                onAnswer = viewModel::answer,
                onRetry = viewModel::retry,
                onTypingInput = viewModel::typingInput,
                onSubmitTyped = viewModel::submitTyped,
                onChooseInstead = viewModel::chooseInstead,
                // Only English -> German questions (the English word is the prompt) have a speaker button.
                speakerSlot = speechAvailable && state.question.question.speakOnShow != null,
                onSpeak = viewModel::speak,
                onEdit = if (editButton && state.editable) {
                    { viewModel.startEditingCurrentWord()?.let(onEditWord) }
                } else {
                    null
                },
                onClose = { confirmCancel = true },
                modifier = if (LocalCompactHeight.current) {
                    modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                } else {
                    contentModifier
                }
            )
        }
        is QuizRoundState.Finished -> {
            BackHandler(onBack = viewModel::backToStart)
            QuizFinished(
                state = state,
                onPlayAgain = viewModel::startRound,
                onBackToStart = viewModel::backToStart,
                modifier = contentModifier
            )
        }
    }
}

private class StartActions(
    val onSelectMode: (QuizMode) -> Unit,
    val onSelectUnit: (UnitChoice) -> Unit,
    val onTogglePart: (String?) -> Unit,
    val onSelectAllParts: () -> Unit,
    val onSelectPageFrom: (Int?) -> Unit,
    val onSelectPageTo: (Int?) -> Unit,
    val onOpenSelection: () -> Unit,
    val onLeaveSelection: () -> Unit,
    val onApplySelection: () -> Unit,
    val onToggleProgress: () -> Unit,
    val onStart: () -> Unit
)

/** "Zurück" on the selection screen after a change; dismissing it stays on the selection screen. */
@Composable
internal fun LeaveSelectionDialog(onApply: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(SelectionTexts.CHANGED_QUESTION) },
        confirmButton = { TextButton(onClick = onApply) { Text(SelectionTexts.DIALOG_APPLY) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(SelectionTexts.DIALOG_DISCARD) } }
    )
}

/** Texts of the selection screen. */
object SelectionTexts {
    const val CHANGED_QUESTION = "Deine Auswahl hat sich geändert. Möchtest du sie übernehmen?"
    const val DIALOG_APPLY = "Übernehmen"
    const val DIALOG_DISCARD = "Verwerfen"
    const val APPLY = "Auswahl übernehmen"
    const val BACK = "Zurück"
    const val MODE_TITLE_TAG = "selection_mode_title"
    const val INFO_TAG = "selection_info"
}

/** Short texts of the start screen. */
object StartTexts {
    /** The book has too few words for a question with suggestions ([MIN_VOCABULARY]). */
    val TOO_FEW_WORDS = "Dein Buch braucht mindestens $MIN_VOCABULARY Wörter."
    const val NO_VERBS = "Noch keine Verben da."

    /** "3 Formen", but no verb of the book has a past participle. */
    const val NO_THREE_FORM_VERBS =
        "Bei deinen Verben fehlt noch die dritte Form. Nur zwei Formen? Stell es um unter Einstellungen → Üben."

    /** "Dein Lernstand" until the first round of the active book ([FirstRound.done]). */
    const val FIRST_ROUND = "Starte deine erste Runde!"
}

/** Test tag of the line [StartTexts.FIRST_ROUND] and of the card "Dein Lernstand". */
const val FIRST_ROUND_TAG = "first_round_line"
const val PROGRESS_CARD_TAG = "progress_card"
const val LEVELS_INFO_TAG = "levels_info"

/** Test tag of the stage progress bar of the avatar card on the start screen. */
const val STAGE_BAR_TAG = "start_stage_bar"

/** The figure only grows from vocabulary points, so it is not shown in the verb mode. */
internal fun showsFigure(mode: QuizMode): Boolean = mode.countsForRewards

/**
 * The triggers of the tips on the start screen for a newcomer: the selection card at once (once
 * the avatar is chosen), "Los geht's" once the selection tip is done ([selectionTipDone]; it then
 * waits for the next visit, see [StartVisit]; vocabulary mode only, it names the round size of
 * words), the mode tiles once the verb mode is shown with verbs ([verbsShown]), "Dein
 * Lernstand" (vocabulary mode only) on the visit after "Das hast du geschafft" is done
 * ([roundResultDone]) and the avatar card once "Dein Lernstand" is done ([levelsDone]). A tip comes once: "Verstanden" marks it done. The closing tip on "?" sits in
 * the top bar ([Tip.ALL_KNOWN], see `VocTrainerApp`). Which of the due tips is shown, and when, is
 * up to the tip host ([StartVisit]).
 */
internal fun startTipTriggers(
    figure: FigureState,
    selectionTipDone: Boolean = false,
    roundResultDone: Boolean = false,
    levelsDone: Boolean = false,
    mode: QuizMode = QuizMode.VOCABULARY,
    verbsShown: Boolean = false
): Map<Tip, Boolean> = mapOf(
    Tip.SELECTION to !figure.needsChoice,
    Tip.START_BUTTON to (selectionTipDone && mode == QuizMode.VOCABULARY),
    Tip.VERBS to (verbsShown && mode == QuizMode.VERBS),
    Tip.LEVELS to (roundResultDone && mode == QuizMode.VOCABULARY),
    Tip.AVATAR_CARD to levelsDone
)

/** The closing tip on "?": once the avatar tip was closed with "Verstanden" (also done by the update). */
internal fun allKnownTriggered(done: Set<Tip>): Boolean = Tip.AVATAR_CARD in done

@Composable
private fun QuizStart(
    state: QuizStartState,
    streak: StreakInfo?,
    figure: FigureState,
    /** Whether the triggers of the start screen tips hold ([startTipTriggers]). */
    tipTriggers: Map<Tip, Boolean>,
    onOpenFigure: () -> Unit,
    onFigureShown: (bookId: String, slot: Int, stage: Int) -> Boolean,
    message: String?,
    expanded: Boolean,
    progressExpanded: Boolean,
    actions: StartActions,
    modifier: Modifier = Modifier
) {
    // While the selection screen is open, "Los geht's" is replaced by its own buttons.
    val editing = expanded && !state.verbsMissing
    // No tip on the selection screen.
    if (editing) BlockTips()
    val density = LocalDensity.current
    // Height of everything above the figure card, so the card fills the rest of the screen.
    var contentHeight by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier = modifier) {
        val screenHeight = maxHeight
        val startScroll = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(startScroll)) {
            Column(
                modifier = Modifier.onSizeChanged { contentHeight = with(density) { it.height.toDp() } },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Not before the avatar is chosen (also "Tipp sofort zeigen" waits).
                val selectionTarget = if (tipTriggers[Tip.SELECTION] == true) Modifier.tipTarget(Tip.SELECTION) else Modifier
                StartContent(
                    state, streak, message, editing, expanded, progressExpanded, actions,
                    selectionModifier = selectionTarget,
                    startModifier = Modifier.tipTarget(Tip.START_BUTTON, triggered = tipTriggers[Tip.START_BUTTON] == true),
                    modesModifier = Modifier.tipTarget(Tip.VERBS, triggered = tipTriggers[Tip.VERBS] == true),
                    // Only on the folded card, which the tip does not unfold.
                    progressModifier = if (progressExpanded) Modifier
                    else Modifier.tipTarget(Tip.LEVELS, triggered = tipTriggers[Tip.LEVELS] == true)
                )
            }
            val current = figure.shown
            val kind = figure.shownKind
            // Only in the basic state: not while editing the selection or with the details open.
            if (showsFigure(state.mode) && !editing && !progressExpanded && current != null && kind != null) {
                var wiggle by remember { mutableIntStateOf(0) }
                LaunchedEffect(current.bookId, current.slot, current.stage) {
                    if (onFigureShown(current.bookId, current.slot, current.stage)) wiggle++
                }
                FigureCard(
                    wiggle = wiggle,
                    nextLocked = figure.nextLocked,
                    waitingForSureWords = figure.waitingForSureWords,
                    kind = kind,
                    stage = current.stage,
                    progress = figure.pointsFraction,
                    // The way to the next stage belongs to the avatar: below its bar.
                    nextStageLine = FigureTexts.homeNextStage(figure.next),
                    height = screenHeight - contentHeight - 16.dp,
                    onClick = onOpenFigure,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .tipTarget(Tip.AVATAR_CARD, triggered = tipTriggers[Tip.AVATAR_CARD] == true),
                    barModifier = Modifier.testTag(STAGE_BAR_TAG)
                )
            }
        }
        ScrollHint(startScroll.canScrollForward)
    }
}

@Composable
private fun StartContent(
    state: QuizStartState,
    streak: StreakInfo?,
    message: String?,
    editing: Boolean,
    expanded: Boolean,
    progressExpanded: Boolean,
    actions: StartActions,
    /** For the selection card (a tip target). */
    selectionModifier: Modifier = Modifier,
    /** For "Los geht's" (a tip target). */
    startModifier: Modifier = Modifier,
    /** For both mode tiles together (a tip target). */
    modesModifier: Modifier = Modifier,
    /** For the card "Dein Lernstand" (a tip target). */
    progressModifier: Modifier = Modifier
) {
    // Hidden while editing, so the open filters read as their own sub screen.
    if (!editing && streak != null) StreakBar(streak)
    // The mode cannot be switched on the selection screen: only its name as a title.
    if (editing) ModeTitle(state.mode) else ModeTiles(selected = state.mode, onSelect = actions.onSelectMode, modifier = modesModifier)
    if (state.loaded && state.verbsMissing) {
        Text(
            text = if (state.noVerbs) StartTexts.NO_VERBS else StartTexts.NO_THREE_FORM_VERBS,
            style = MaterialTheme.typography.titleMedium
        )
    } else if (expanded) {
        LaunchedEffect(Unit) { SelectionHint.consume() }
        QuizFilters(state, actions)
    } else {
        // Pulses a few times after an import, so the learner notices the selection.
        val hint by SelectionHint.pending.collectAsState()
        val pulse = remember { Animatable(0f) }
        LaunchedEffect(hint) {
            if (!hint) return@LaunchedEffect
            repeat(3) {
                pulse.animateTo(1f, tween(500))
                pulse.animateTo(0f, tween(500))
            }
            SelectionHint.consume()
        }
        Card(
            modifier = selectionModifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .clickable(
                    onClickLabel = "Auswahl ändern",
                    role = Role.Button,
                    onClick = actions.onOpenSelection
                ),
            colors = CardDefaults.cardColors(
                containerColor = lerp(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.primaryContainer,
                    pulse.value
                )
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = selectionSummary(state.filter, state.partOptions, state.wordCount, state.mode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    if (state.loaded && state.tooFewWords) {
        Text(
            text = StartTexts.TOO_FEW_WORDS,
            color = MaterialTheme.colorScheme.error
        )
    }
    message?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
    // The system back works like "Zurück".
    BackHandler(enabled = editing, onBack = actions.onLeaveSelection)
    if (editing) {
        SelectionButtons(onBack = actions.onLeaveSelection, onApply = actions.onApplySelection)
    } else {
        Button(
            onClick = actions.onStart,
            enabled = state.loaded && !state.tooFewWords && state.wordCount > 0,
            modifier = startModifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
        ) {
            Text("Los geht's", style = MaterialTheme.typography.titleLarge)
        }
    }
    // Hidden while editing, so the open filters read as their own sub screen.
    if (!editing && !state.verbsMissing) {
        LearningProgressCard(state, progressExpanded, actions.onToggleProgress, progressModifier)
    }
}

@Composable
private fun QuizFilters(state: QuizStartState, actions: StartActions) {
    val filter = state.filter
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UnitPartPageFilters(
            unitChoices = state.unitChoices,
            partOptions = state.partOptions,
            pages = state.pages,
            filter = filter,
            onSelectUnit = actions.onSelectUnit,
            onTogglePart = actions.onTogglePart,
            onSelectAllParts = actions.onSelectAllParts,
            onSelectPageFrom = actions.onSelectPageFrom,
            onSelectPageTo = actions.onSelectPageTo
        )
        Text(
            text = countLabel(state.wordCount, state.mode, " ausgewählt"),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private val MODES = listOf(
    Triple(QuizMode.VOCABULARY, "Vokabeln", R.drawable.ic_book),
    Triple(QuizMode.VERBS, "Verben", R.drawable.ic_shuffle)
)

/**
 * The selection screen only names its mode: a plain title, not highlighted and not clickable; the
 * ⓘ behind it opens "Vokabeln auswählen" / "Verben auswählen".
 */
@Composable
private fun ModeTitle(mode: QuizMode) {
    val (_, label, icon) = MODES.first { it.first == mode }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    if (showInfo) SelectionInfoDialog(mode) { showInfo = false }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .testTag(SelectionTexts.MODE_TITLE_TAG)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
        InfoButton(onClick = { showInfo = true }, modifier = Modifier.testTag(SelectionTexts.INFO_TAG))
    }
}

/** At the bottom of the selection screen: "Zurück" (small, left) and "Auswahl übernehmen" (main button, right). */
@Composable
private fun SelectionButtons(onBack: () -> Unit, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(SelectionTexts.BACK, style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = onApply,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
        ) {
            Text(SelectionTexts.APPLY, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/** Two large tiles to choose the quiz mode; never green (green means "correct"). */
@Composable
private fun ModeTiles(selected: QuizMode, onSelect: (QuizMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MODES.forEach { (mode, label, icon) ->
            val isSelected = mode == selected
            val scheme = MaterialTheme.colorScheme
            Surface(
                color = if (isSelected) scheme.primary else scheme.surfaceContainerHigh,
                contentColor = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = isSelected, onClick = { onSelect(mode) }, role = Role.Tab)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/**
 * "Dein Lernstand" of the current selection; tapping shows the details per level. Folded, it also
 * shows what the next round brings.
 */
@Composable
private fun LearningProgressCard(
    state: QuizStartState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = state.progress
    if (progress.total == 0) return
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    if (showInfo) {
        LevelsInfoDialog(state.mode, state.threeVerbForms) { showInfo = false }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PROGRESS_CARD_TAG)
            .clip(CardDefaults.shape)
            .clickable(
                onClickLabel = if (expanded) "Details ausblenden" else "Details anzeigen",
                role = Role.Button,
                onClick = onToggle
            ),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dein Lernstand", style = MaterialTheme.typography.titleMedium)
                // Own click target: tapping ⓘ must not fold the card.
                if (expanded) InfoButton(onClick = { showInfo = true }, modifier = Modifier.testTag(LEVELS_INFO_TAG))
                Spacer(Modifier.weight(1f))
                Icon(
                    painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant
                )
            }
            val summary: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProgressBar(progress.percent / 100f, Modifier.weight(1f))
                        Text("${progress.percent} % gelernt", style = MaterialTheme.typography.titleSmall)
                    }
                    // The bar with "X % gelernt" says enough; no extra "N von M gelernt" line.
                    if (!expanded) {
                        state.nextRoundLine?.let { line ->
                            Text(text = line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!expanded && state.mode == QuizMode.VOCABULARY && !state.firstRoundDone) {
                // Before the first round one line replaces bar and next-round line; the hidden
                // summary keeps the height of the card, so nothing moves after the first round.
                Box(contentAlignment = Alignment.CenterStart) {
                    Box(modifier = Modifier.alpha(0f).clearAndSetSemantics {}) { summary() }
                    Text(
                        StartTexts.FIRST_ROUND,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag(FIRST_ROUND_TAG)
                    )
                }
            } else {
                summary()
            }
            if (expanded) {
                // The same colour groups as in "Was bedeuten die Level?": how the word is asked.
                val kindColors = LocalLevelKindColors.current
                InfoTexts.levelGroups(InfoTexts.levelRows(state.mode, state.threeVerbForms)).forEach { group ->
                    val color = kindColors.of(group.kind)
                    Surface(color = color.container, contentColor = color.onContainer, shape = MaterialTheme.shapes.medium) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(group.kind.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            val levelRow: @Composable (LevelRow) -> Unit = { row ->
                                LevelCountRow(row, progress.countsPerLevel[row.level], progress.total, color, state.hasTypeable)
                            }
                            // The sure levels are the learner's target: one shared frame "Ziel".
                            val (practice, target) = group.rows.partition { it.level < LearningProgress.SURE_LEVEL }
                            practice.forEach { levelRow(it) }
                            if (target.isNotEmpty()) {
                                GoalFrame(background = color.container) { target.forEach { levelRow(it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One level in the details of "Dein Lernstand": name, bar and number of words. */
@Composable
private fun LevelCountRow(row: LevelRow, count: Int, total: Int, color: KindColor, hasTypeable: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Level ${row.level}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
        Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp))
        ProgressBar(
            count.toFloat() / total, Modifier.weight(1f),
            barColor = color.onContainer, trackColor = color.onContainer.copy(alpha = 0.2f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
        // Typeable words cannot reach these levels with choosing yet.
        if (hasTypeable) {
            Box(modifier = Modifier.width(18.dp)) {
                if (Reachability.levelLockedForTypeable(row.level)) LockIcon()
            }
        }
    }
}

/**
 * Thin rounded frame in the accent colour with the label "Ziel" on its top edge (like a fieldset
 * legend); [background] is the colour behind it, so the label cuts the line. Kept tight, so the
 * card grows only by a few dp.
 */
@Composable
private fun GoalFrame(background: Color, content: @Composable ColumnScope.() -> Unit) {
    val line = LocalGoalFrameColor.current
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = Modifier.padding(top = GOAL_LABEL_HALF)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, line, shape)
                .padding(start = 6.dp, end = 6.dp, top = GOAL_LABEL_HALF, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
        Text(
            text = "Ziel",
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = GOAL_LABEL_LINE),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 10.dp)
                .offset(y = -GOAL_LABEL_HALF)
                .background(background)
                .padding(horizontal = 4.dp)
        )
    }
}

private val GOAL_LABEL_LINE = 12.sp
private val GOAL_LABEL_HALF = 6.dp

// Explicit colours: the default track is secondaryContainer, which is greenish in "Gaming dunkel"
// and would look like the "correct" colour.
@Composable
private fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier,
        color = barColor,
        trackColor = trackColor,
        // No gap and end dot: with many empty level bars they only add noise.
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** Test tags of the question screen, for the layout test on a device. */
object QuizTags {
    const val HEADER = "quiz_header"
    const val PROMPT = "quiz_prompt"
    const val TOP = "quiz_top"
    const val BOTTOM = "quiz_bottom"
    const val INPUT = "quiz_input"
    const val CHECK = "quiz_check"
    const val CHOICE = "quiz_choice"
    const val SPEAKER = "quiz_speaker"
    const val RESULT = "quiz_result"
    const val FEEDBACK = "quiz_feedback"
    const val VERB_STEPS = "quiz_verb_steps"
    const val FIRST_STEP_MISSED = "quiz_first_step_missed"
    const val VERB_FORM_LABEL = "quiz_verb_form_label"

    /** Test app only: the edit pencil in the question header. */
    const val EDIT_BUTTON = "quiz_edit_button"
}

@Composable
private fun QuizQuestion(
    state: QuizRoundState.Asking,
    vibration: Boolean,
    /** Test app only: a small dot on the correct option before answering; no effect on the logic. */
    markCorrect: Boolean,
    onAnswer: (Int) -> Unit,
    onRetry: () -> Unit,
    onTypingInput: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    onChooseInstead: () -> Unit,
    /** English prompt and an English voice is available: the speaker button is shown next to the word. */
    speakerSlot: Boolean,
    onSpeak: () -> Unit,
    /** Edit button in the top right corner; null hides it (verbs, or switched off in the settings). */
    onEdit: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val question = state.question.question
    val typing = state.typing
    val typingTask = question.typing
    // Typing (before "Mit Vorschlägen"); after it the question looks like any question with suggestions.
    val typingLayout = typing != null && typingTask != null && typing.phase != TypingPhase.CHOICE
    val compact = LocalCompactHeight.current
    // One structure for every question: a fixed header, the word at the top and the answer part at the
    // bottom, lifted above the keyboard. Only the answer part follows the keyboard; the Scaffold padding
    // already includes the navigation bar.
    Column(
        modifier = modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        verticalArrangement = compactSpacing(compact, normal = 12.dp, small = 6.dp)
    ) {
        QuestionHeader(state, onEdit, onClose)
        // Top-aligned, so the word stays in the same place in every phase and mode; it scrolls only inside
        // itself if it does not fit (large font, small display).
        val topScroll = remember(state.question) { ScrollState(0) }
        // Only if the upper part does not fit (very large font): after a wrong typed answer or "Fast richtig" it
        // scrolls to its end, so the correct word below the frame is visible for copying; again when the hint
        // after a wrong copy appears.
        val copying = typing?.phase == TypingPhase.COPYING
        // Compact: the place of the upper part and of the feedback lines, to scroll just as far as the lines need
        // (the end of the slot may be empty); again when the keyboard changes the height of the upper part.
        val viewport = remember(state.question) { arrayOfNulls<LayoutCoordinates>(1) }
        val feedbackLines = remember(state.question) { arrayOfNulls<LayoutCoordinates>(1) }
        var viewportHeight by remember(state.question) { mutableIntStateOf(0) }
        LaunchedEffect(state.question, copying, typing?.copyMismatch, viewportHeight) {
            if (!compact) {
                if (copying && topScroll.maxValue > 0) topScroll.animateScrollTo(topScroll.maxValue)
            } else if (copying) {
                // The lines of the new phase are laid out in the coming frames.
                repeat(2) { withFrameNanos { } }
                val top = viewport[0]?.takeIf { it.isAttached } ?: return@LaunchedEffect
                val lines = feedbackLines[0]?.takeIf { it.isAttached } ?: return@LaunchedEffect
                topScroll.animateScrollTo(
                    compactCopyScrollTarget(
                        current = topScroll.value,
                        linesBottom = lines.positionInWindow().y + lines.size.height,
                        viewportBottom = top.positionInWindow().y + top.size.height,
                        max = topScroll.maxValue
                    )
                )
            }
        }
        Column(
            modifier = Modifier
                .testTag(QuizTags.TOP)
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (compact) {
                        Modifier
                            .onGloballyPositioned { viewport[0] = it }
                            .onSizeChanged { viewportHeight = it.height }
                            .compactFadeEdge(topScroll)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(topScroll),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = compactSpacing(compact, normal = 12.dp, small = 6.dp)
        ) {
            Row(
                modifier = Modifier.heightIn(min = if (compact) 40.dp else 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = question.prompt,
                    // The same style in both answer types.
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag(QuizTags.PROMPT)
                )
                if (speakerSlot) {
                    SlimIconButtons(compact) {
                        IconButton(onClick = onSpeak, modifier = Modifier.testTag(QuizTags.SPEAKER)) {
                            Icon(painterResource(R.drawable.ic_volume_up), contentDescription = "Vorsprechen")
                        }
                    }
                }
            }
            question.subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            // Compact: the label of the asked form above the answers takes the place of this line.
            if (!compact) {
                question.instruction?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (question.secondStep != null) VerbSteps(state)
            if (typingLayout) {
                TypingFeedback(
                    state, typingTask, typing,
                    linesModifier = Modifier.onGloballyPositioned { feedbackLines[0] = it }
                )
            } else {
                ChoiceFeedback(state)
            }
        }
        Column(
            modifier = Modifier
                .testTag(QuizTags.BOTTOM)
                .fillMaxWidth(),
            verticalArrangement = compactSpacing(compact, normal = 12.dp, small = 8.dp)
        ) {
            if (typingLayout) {
                TypingControls(
                    state = state,
                    typing = typing,
                    vibration = vibration,
                    onInput = onTypingInput,
                    onSubmit = onSubmitTyped,
                    onChoose = onChooseInstead,
                    // The tip "Selbst tippen" points at "Mit Vorschlägen" once it can be tapped.
                    choiceModifier = Modifier.tipTarget(Tip.TYPING, key = state.question)
                )
            } else {
                ChoiceOptions(state, vibration, markCorrect, onAnswer, onRetry)
            }
        }
    }
}

/** Close button, "Frage n von m" / "Wiederholung" and the edit button; never scrolls. */
@Composable
private fun QuestionHeader(state: QuizRoundState.Asking, onEdit: (() -> Unit)?, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(QuizTags.HEADER),
        contentAlignment = Alignment.Center
    ) {
        // Compact: one slim row.
        SlimIconButtons(LocalCompactHeight.current) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Runde abbrechen")
            }
            val repeat = state.question.isRepeat
            Text(
                text = if (repeat) "Wiederholung"
                else "Frage ${state.question.number} von ${state.totalWords}",
                style = MaterialTheme.typography.labelLarge,
                // The tip "Wiederholung" points at the label of the first repetition.
                modifier = if (repeat) Modifier.tipTarget(Tip.REPEAT, key = state.question) else Modifier
            )
            if (onEdit != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.align(Alignment.CenterEnd).debugOutline().testTag(QuizTags.EDIT_BUTTON)
                ) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Vokabel bearbeiten", tint = TestOrange)
                }
            }
        }
    }
}

/** Labels of the two steps of a verb with three forms. */
object VerbStepTexts {
    const val SIMPLE_PAST = "simple past"
    const val PAST_PARTICIPLE = "past participle"

    /** Compact layout: the asked form, directly above the answers ([verbFormLabel]). */
    const val SIMPLE_PAST_LABEL = "Simple Past?"
    const val PAST_PARTICIPLE_LABEL = "Past Participle?"

    /** Second step right, but the first answer of the first step was wrong: why the verb does not rise. */
    const val FIRST_STEP_MISSED = "Das Simple Past war noch nicht richtig."

    /** Not found yet. */
    const val OPEN = "?"
}

/**
 * The form asked right now, in the compact layout: every verb question has an instruction line (with both "2
 * Formen" and "3 Formen"), which this label replaces there. Null for a vocabulary question.
 */
internal fun verbFormLabel(instruction: String?, inSecondStep: Boolean): String? = when {
    instruction == null -> null
    inSecondStep -> VerbStepTexts.PAST_PARTICIPLE_LABEL
    else -> VerbStepTexts.SIMPLE_PAST_LABEL
}

/**
 * Verbs with three forms: both forms as two fixed lines, the current step highlighted. The simple
 * past appears in its line once found, the past participle once answered correctly; the lines are
 * there from the start, so nothing moves.
 */
@Composable
private fun VerbSteps(state: QuizRoundState.Asking) {
    val question = state.question.question
    val second = question.secondStep ?: return
    val past = question.options[question.correctIndex]
    val participle = second.options[second.correctIndex]
    Column(
        modifier = Modifier.testTag(QuizTags.VERB_STEPS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VerbStepLine(VerbStepTexts.SIMPLE_PAST, past.takeIf { state.inSecondStep }, active = !state.inSecondStep)
        VerbStepLine(
            VerbStepTexts.PAST_PARTICIPLE,
            participle.takeIf { state.inSecondStep && state.phase == AnswerPhase.CORRECT },
            active = state.inSecondStep
        )
    }
}

@Composable
private fun VerbStepLine(label: String, value: String?, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // Only the colour marks the current step: a bolder label would be wider and shift the line.
            color = if (active) scheme.primary else scheme.onSurfaceVariant
        )
        Text(
            text = value ?: VerbStepTexts.OPEN,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
            color = if (value != null) scheme.onSurface else scheme.onSurfaceVariant
        )
    }
}

/**
 * Below the word: the solution after a wrong choice, "Finde die richtige Antwort." in the retry,
 * and why a verb with three forms does not rise although its second step was right.
 */
@Composable
private fun ChoiceFeedback(state: QuizRoundState.Asking) {
    if (state.firstStepMissed && state.phase == AnswerPhase.CORRECT) {
        Text(
            text = VerbStepTexts.FIRST_STEP_MISSED,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(QuizTags.FIRST_STEP_MISSED)
        )
        return
    }
    when (state.phase) {
        AnswerPhase.SOLUTION -> Text(
            text = state.solution,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        AnswerPhase.RETRY -> Text(
            text = "Finde die richtige Antwort.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        else -> Unit
    }
}

/**
 * The suggested answers, the result banner and the place of "Nochmal versuchen" below them, reserved from
 * the start, so the options never move after an answer.
 */
@Composable
private fun ChoiceOptions(
    state: QuizRoundState.Asking,
    vibration: Boolean,
    markCorrect: Boolean,
    onAnswer: (Int) -> Unit,
    onRetry: () -> Unit
) {
    val selectedIndex = state.selectedIndex
    val correctIndex = state.correctIndex
    val phase = state.phase
    val answered = phase == AnswerPhase.CORRECT || phase == AnswerPhase.SOLUTION
    val answerColors = LocalAnswerColors.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val answerVibration = remember(context) { AnswerVibration(context) }
    val shake = remember(state.question) { Animatable(0f) }
    val compact = LocalCompactHeight.current

    LaunchedEffect(state.question, selectedIndex) {
        if (selectedIndex == null) return@LaunchedEffect
        val correct = selectedIndex == correctIndex
        // Only wrong answers vibrate, also in the "Nochmal versuchen" phase.
        if (vibration && !correct) answerVibration.wrong()
        if (!correct) {
            shake.animateTo(0f, keyframes {
                durationMillis = 300
                -14f at 50
                14f at 100
                -10f at 150
                10f at 200
                -4f at 250
            })
        }
    }

    if (compact) {
        // One line from the start of the question, in the same place in both steps; it never scrolls away.
        verbFormLabel(state.question.question.instruction, state.inSecondStep)?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(QuizTags.VERB_FORM_LABEL)
            )
        }
    }
    state.options.forEachIndexed { index, option ->
        val (container, content) = when {
            !answered -> answerColors.optionContainer to answerColors.onOptionContainer
            index == correctIndex -> answerColors.correctContainer to answerColors.onCorrectContainer
            index == selectedIndex -> scheme.errorContainer to scheme.onErrorContainer
            else -> scheme.surfaceVariant to scheme.onSurfaceVariant
        }
        val shakeModifier = if (index == selectedIndex && phase == AnswerPhase.SOLUTION) {
            Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }
        } else {
            Modifier
        }
        Button(
            onClick = { onAnswer(index) },
            enabled = !answered,
            // Light option colours are close to the background; the outline keeps the shape clear.
            border = if (answered) null else BorderStroke(1.dp, scheme.outline),
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container,
                disabledContentColor = content
            ),
            modifier = shakeModifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 52.dp else 64.dp)
        ) {
            if (markCorrect && !answered && index == correctIndex) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TestOrange)
                )
            }
            Text(text = option, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
    if (compact) {
        ResultBanner(answerResult(state), Modifier.fillMaxWidth(), verticalPadding = 2.dp)
    } else {
        ResultBanner(answerResult(state), Modifier.fillMaxWidth())
    }
    val retryHeight = if (compact) 48.dp else 56.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = retryHeight)
    ) {
        if (phase == AnswerPhase.SOLUTION) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = retryHeight)
            ) {
                Text("Nochmal versuchen", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun starHeadline(stars: Int): String = when (stars) {
    3 -> "Super gemacht!"
    2 -> "Stark!"
    1 -> "Gut, weiter so!"
    else -> "Nächstes Mal klappt's besser."
}

@Composable
private fun QuizFinished(
    state: QuizRoundState.Finished,
    onPlayAgain: () -> Unit,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stars = RoundStars.forCredit(state.starCredit, state.totalWords)
    var showPointsInfo by rememberSaveable { mutableStateOf(false) }
    if (showPointsInfo) {
        PointsInfoDialog { showPointsInfo = false }
    }
    // Only the tip "Das hast du geschafft" may come, and a dialog blocks it. Celebration, avatar growth and
    // new badges are set together with "Details" (all static), so the result is complete once
    // "Details" exists and the tip host waits until it has been still.
    BlockTips(allow = if (showPointsInfo) emptySet() else setOf(Tip.ROUND_RESULT))
    ScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(RoundStars.MAX) { index ->
                val earned = index < stars
                Icon(
                    painter = painterResource(if (earned) R.drawable.ic_star else R.drawable.ic_star_border),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(72.dp)
                )
            }
        }
        Text(
            text = starHeadline(stars),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        state.celebration?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.figureGrowth?.let { FigureGrowthBlock(it) }
        // Only vocabulary rounds earn badges, so the list is empty in verb training.
        state.newBadges.forEach { NewBadgeLine(it) }
        // The points line and "Details" are one spotlight area of the tip "Das hast du geschafft"
        // (vocabulary rounds only: it is about points and the avatar).
        val resultTip = state.mode.countsForRewards && state.roundPoints != null && !state.details.isNullOrEmpty()
        Column(
            modifier = if (resultTip) Modifier.tipTarget(Tip.ROUND_RESULT) else Modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Verb training gives no points (it is separate from the rewards): no points line there.
            if (state.mode.countsForRewards) {
                state.roundPoints?.let { points ->
                    CenteredWithInfo(onInfo = { showPointsInfo = true }) {
                        Text(
                            text = PracticeTexts.roundPoints(points),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Known once all answers of the round are stored (in both modes).
            state.details?.let { RoundChanges(it, verbs = state.mode == QuizMode.VERBS) }
        }
        val noun = when {
            state.mode == QuizMode.VERBS -> if (state.totalWords == 1) "Verb" else "Verben"
            state.totalWords == 1 -> "Wort"
            else -> "Wörtern"
        }
        Text(
            text = "Du hast ${state.correctOnFirstAttempt} von ${state.totalWords} $noun auf Anhieb gewusst.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (RoundStars.showsReducedHint(stars, state.reducedCredits)) {
            Text(
                text = RoundStars.REDUCED_HINT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.wrongWords.isNotEmpty()) {
            Text(text = "Beim ersten Versuch falsch:", style = MaterialTheme.typography.titleMedium)
            state.wrongWords.forEach { word ->
                val answer = word.verbForms?.simplePast ?: GermanText.withoutGenderSuffix(word.mainMeaning)
                Text(text = "${word.english} – $answer")
            }
        }
        Spacer(modifier = Modifier.heightIn(min = 8.dp))
        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text("Noch eine Runde", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onClick = onBackToStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text("Zum Start", style = MaterialTheme.typography.titleMedium)
        }
    }
}
