package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import io.github.markushornbach.voctrainer.R
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.markushornbach.voctrainer.data.FigureGrowth
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.ui.theme.LocalFigureAccent

/**
 * Full-screen, final choice of the figure; no skip button. [title] see [FigureTexts.choiceTitle];
 * [note]: an extra muted line (test app: the simulated first start).
 */
@Composable
fun FigureChoice(title: String, onChoose: (FigureKind) -> Unit, modifier: Modifier = Modifier, note: String? = null) {
    BlockTips()
    var asking by rememberSaveable { mutableStateOf<FigureKind?>(null) }
    asking?.let { kind ->
        AlertDialog(
            onDismissRequest = { asking = null },
            text = { Text(kind.choiceQuestion) },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    onChoose(kind)
                }) { Text("Ja") }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Zurück") } }
        )
    }
    ScrollColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            (FigureTexts.CHOICE_LINES + listOfNotNull(note)).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        FigureKind.entries.forEach { kind ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardDefaults.shape)
                    .clickable(role = Role.Button, onClickLabel = "Auswählen") { asking = kind },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painterResource(FigureImages.forStage(kind, FigureImages.CHOICE_STAGE)),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp)
                    )
                    Column {
                        Text(kind.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            kind.startLine,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Figure card at the bottom of the start screen: the current figure, one bar to the next stage and
 * below it [nextStageLine] (what is still missing, if anything); opens "Mein Avatar". [height] is
 * the space left on the screen; the card grows beyond it rather than cutting the line off.
 */
@Composable
fun FigureCard(
    kind: FigureKind,
    stage: Int,
    progress: Float,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Stufe 4: noch 120 Punkte" below the bar (one line); null = no line. */
    nextStageLine: String? = null,
    /** Increased to let the figure wiggle once (after a new stage). */
    wiggle: Int = 0,
    /** The next stage cannot be reached today ([FigureState.nextLocked]): lock instead of its number. */
    nextLocked: Boolean = false,
    /** Full bar, but sure words are missing ([FigureState.waitingForSureWords]): lock at the end of the bar. */
    waitingForSureWords: Boolean = false,
    /** For the pill with the stage progress bar (e.g. a tip target). */
    barModifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val lineStyle = MaterialTheme.typography.bodyMedium
    // Room for the line is taken from the figure, so the card still fits the screen.
    val lineSpace = if (nextStageLine == null) 0.dp
    else FIGURE_PILL_GAP + with(LocalDensity.current) { lineStyle.lineHeight.toDp() }
    val chrome = FIGURE_AREA_CHROME + lineSpace
    val areaHeight = maxOf(height, FIGURE_MIN + chrome)
    val imageSize = (areaHeight - chrome).coerceIn(FIGURE_MIN, FIGURE_MAX)
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val context = LocalContext.current
    LaunchedEffect(wiggle) {
        if (wiggle == 0 || !animationsEnabled(context)) return@LaunchedEffect
        launch {
            rotation.animateTo(0f, keyframes {
                durationMillis = WIGGLE_MILLIS
                -8f at 100
                8f at 250
                -5f at 400
                3f at 550
            })
        }
        scale.animateTo(1f, keyframes {
            durationMillis = WIGGLE_MILLIS
            1.08f at 200
            0.98f at 450
        })
    }
    // The ⓘ lies over the corner of the card, so nothing in the card moves.
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = areaHeight)
                .clip(FIGURE_AREA_SHAPE)
                .clickable(onClickLabel = "Mein Avatar öffnen", role = Role.Button, onClick = onClick)
                .padding(FIGURE_AREA_PADDING),
            verticalArrangement = Arrangement.spacedBy(FIGURE_PILL_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painterResource(FigureImages.forStage(kind, stage)),
                contentDescription = "Mein Avatar",
                modifier = Modifier
                    .size(imageSize)
                    .graphicsLayer {
                        rotationZ = rotation.value
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
            // The pill looks like a button: outline and arrow; the whole area is the click target.
            Row(
                modifier = barModifier
                    .fillMaxWidth(FIGURE_PILL_WIDTH)
                    .height(FIGURE_PILL_HEIGHT)
                    .background(scheme.surfaceContainerHigh, CircleShape)
                    .border(1.dp, scheme.primary, CircleShape)
                    .semantics { if (nextLocked) stateDescription = NEXT_STAGE_LOCKED_STATE }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stage.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                StageProgressBar(
                    progress = progress,
                    color = LocalFigureAccent.current,
                    locked = waitingForSureWords,
                    modifier = Modifier.weight(1f)
                )
                if (nextLocked && stage < FigureRules.MAX_STAGE) {
                    LockIcon(size = 16.dp, description = null, tint = scheme.onSurfaceVariant)
                } else {
                    // At the last stage the bar is full and shows 10 at both ends.
                    Text(
                        (stage + 1).coerceAtMost(FigureRules.MAX_STAGE).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            nextStageLine?.let {
                Text(
                    text = it,
                    style = lineStyle,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    // Always one line: a very large font or a narrow screen shrinks the text instead.
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = lineStyle.fontSize),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Bar towards the next stage with a round marker at the current position, so 0 % (marker on the
 * left) and 100 % (marker on the right) are told apart in every theme. The track has an outline
 * and the marker a ring in the background colour. [locked]: the points are there, but sure words
 * are missing – a lock inside the marker (which then sits at the right end), so nothing moves.
 */
@Composable
fun StageProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 8.dp,
    thumbSize: Dp = 16.dp,
    locked: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val value = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .height(thumbSize)
            .progressSemantics(value)
    ) {
        val track = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .align(Alignment.CenterStart)
        Box(
            track
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest)
                .border(1.dp, scheme.outline, CircleShape)
        )
        // The fill reaches the middle of the marker, so it never peeks out behind it.
        val travel = (maxWidth - thumbSize).coerceAtLeast(0.dp)
        val thumbStart = travel * value
        Box(
            Modifier
                .width(thumbStart + thumbSize / 2)
                .height(barHeight)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            Modifier
                .offset(x = thumbStart)
                .size(thumbSize)
                .align(Alignment.CenterStart)
                .background(color, CircleShape)
                .border(2.dp, scheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (locked) {
                LockIcon(
                    size = thumbSize * 0.6f,
                    description = SURE_WORDS_NEEDED,
                    tint = if (color.luminance() > 0.4f) Color.Black else Color.White
                )
            }
        }
    }
}

/** False if the system turned animations off (animator duration scale 0, e.g. "Remove animations"). */
internal fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

private const val WIGGLE_MILLIS = 700
private const val NEXT_STAGE_LOCKED_STATE = "Nächste Stufe noch nicht erreichbar"
private const val SURE_WORDS_NEEDED = "Noch gelernte Wörter nötig"
private val FIGURE_MIN = 96.dp
private val FIGURE_MAX = 180.dp
private val FIGURE_AREA_SHAPE = RoundedCornerShape(24.dp)
private val FIGURE_AREA_PADDING = 8.dp
private val FIGURE_PILL_GAP = 8.dp
private val FIGURE_PILL_HEIGHT = 36.dp
private const val FIGURE_PILL_WIDTH = 0.8f

/** Padding, gap and pill around the image. */
private val FIGURE_AREA_CHROME = FIGURE_AREA_PADDING * 2 + FIGURE_PILL_GAP + FIGURE_PILL_HEIGHT

/** Page "Mein Avatar": current stage, points, what the next stage needs, the collection and the badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FigureScreen(viewModel: QuizViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val state by viewModel.figureState.collectAsState()
    val badges by viewModel.badgeState.collectAsState()
    // Shows a stage or badge reached outside a round (e.g. after a restore); never celebrated here.
    LaunchedEffect(Unit) { viewModel.refreshFigure() }

    Scaffold(topBar = { BackTopBar(title = "Mein Avatar", onBack = onBack) }) { innerPadding ->
        ScrollColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = 16.dp,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // While paused, the last finished avatar stays visible (stage 10, "Fertig ausgewachsen!").
            val current = state.shown
            val kind = state.shownKind
            var info by rememberSaveable { mutableStateOf(FigureInfo.NONE) }
            if (info == FigureInfo.BADGES) BadgesInfoDialog { info = FigureInfo.NONE }
            if (current != null && kind != null) {
                Image(
                    painterResource(FigureImages.forStage(kind, current.stage)),
                    contentDescription = kind.displayName,
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.CenterHorizontally)
                )
                when (info) {
                    FigureInfo.STAGES -> FigureInfoDialog(current.stage, state.reach) { info = FigureInfo.NONE }
                    FigureInfo.POINTS -> PointsInfoDialog { info = FigureInfo.NONE }
                    FigureInfo.BADGES, FigureInfo.NONE -> Unit
                }
                CenteredWithInfo(onInfo = { info = FigureInfo.STAGES }) {
                    Text(
                        text = FigureTexts.stageLine(kind, current.stage),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                CenteredWithInfo(onInfo = { info = FigureInfo.POINTS }) {
                    Text(
                        text = FigureTexts.pointsAndLearned(state.totalPoints, state.learnedWords),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                if (state.next != null) {
                    StageProgressBar(
                        progress = state.pointsFraction,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        barHeight = 10.dp,
                        thumbSize = 20.dp,
                        locked = state.waitingForSureWords
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.nextLocked) LockIcon()
                    Text(
                        text = FigureTexts.nextStage(state.next),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (state.nextLocked) {
                    Text(
                        text = FigureTexts.NEXT_STAGE_LOCKED,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (state.paused) {
                Text(
                    text = FigureTexts.PAUSED,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (AppLook.testMarkers) {
                DebugButton(onClick = viewModel::addTestPoints, label = "+100 Punkte", modifier = Modifier.align(Alignment.End))
            }
            if (badges.loaded) {
                BadgeSection(badges.rows, onInfo = { info = FigureInfo.BADGES }, modifier = Modifier.padding(top = 12.dp))
            }
            Text(
                text = "Meine Sammlung",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (state.collection.isEmpty()) {
                Text(
                    text = FigureTexts.EMPTY_COLLECTION,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Side by side while there is room, then on to the next row.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.collection.forEach { figure ->
                        val finishedKind = figure.figureKind ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                painterResource(FigureImages.forStage(finishedKind, figure.stage)),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(finishedKind.displayName, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

private enum class FigureInfo { NONE, STAGES, POINTS, BADGES }

/** A centred line with an ⓘ behind it. */
@Composable
fun CenteredWithInfo(onInfo: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) { content() }
        InfoButton(onClick = onInfo)
    }
}

/** Result screen: the figure reached a new stage in this round. */
@Composable
fun FigureGrowthBlock(growth: FigureGrowth, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(FigureImages.forStage(growth.kind, growth.stage)),
            contentDescription = null,
            modifier = Modifier.size(140.dp)
        )
        Text(
            text = FigureTexts.grown(growth.kind, growth.stage, growth.finished),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}
