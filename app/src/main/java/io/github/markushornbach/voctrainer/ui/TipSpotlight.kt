package io.github.markushornbach.voctrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.TipState
import io.github.markushornbach.voctrainer.settings.VerbFormMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Test tags of the spotlight. */
object TipTags {
    const val CARD = "tip_card"
    const val SCRIM = "tip_scrim"

    /** An empty box exactly over the cut-out target (with its margin). */
    const val HOLE = "tip_hole"
}

/**
 * Where the targets of the tips are and what blocks tips right now; one per app, provided by
 * [LocalTipController] and shown by [TipHost]. Screens only use [tipTarget] and [BlockTips].
 */
@Stable
class TipController(
    /** The "one tip per visit" rule; held by the [QuizViewModel], so a recreation does not reset it. */
    val visit: StartVisit = StartVisit()
) {
    internal class Target(val requester: BringIntoViewRequester, val key: Any? = null) {
        /** The trigger of the tip holds (e.g. the first word is learned); see [TipState.isDue]. */
        var triggered by mutableStateOf(false)

        /** Bounds in root coordinates; null until laid out. */
        var bounds by mutableStateOf<Rect?>(null)
        var fullyVisible by mutableStateOf(false)

        /** Closed without "Verstanden": waits until the target appears again (the next occasion). */
        var closed by mutableStateOf(false)
    }

    private class Blocker(val allow: Set<Tip>)

    internal val targets = mutableStateMapOf<Tip, Target>()
    private val blockers = mutableStateListOf<Blocker>()

    /** The tip on screen; null = none. */
    var shown by mutableStateOf<Tip?>(null)
        internal set

    internal fun register(tip: Tip, target: Target) {
        targets[tip] = target
    }

    internal fun unregister(tip: Tip, target: Target) {
        if (targets[tip] === target) targets.remove(tip)
    }

    internal fun block(allow: Set<Tip>): Any = Blocker(allow).also { blockers += it }

    internal fun unblock(token: Any) {
        blockers.remove(token)
    }

    internal fun blocked(tip: Tip): Boolean = blockers.any { tip !in it.allow }

    /** The tip of [Tip.IN_QUESTION] shown for the occasion (question) with this key; at most one per question. */
    private var questionTip by mutableStateOf<Pair<Any?, Tip>?>(null)

    internal fun recordInQuestion(tip: Tip, target: Target) {
        if (tip in Tip.IN_QUESTION) questionTip = target.key to tip
    }

    /** Another tip of [Tip.IN_QUESTION] was already shown in the question of [tip]'s target. */
    internal fun otherTipInQuestion(tip: Tip): Boolean {
        if (tip !in Tip.IN_QUESTION) return false
        val (key, shownTip) = questionTip ?: return false
        return shownTip != tip && key == targets[tip]?.key
    }
}

val LocalTipController = staticCompositionLocalOf<TipController?> { null }

/**
 * Marks the element [tip] points at. [triggered]: the tip's own trigger holds (a pending tip comes
 * either way). The element is scrolled into view before the tip opens, and the tip is shown only
 * while the element is laid out and fully visible. [key]: a new key is a new occasion (e.g. the next
 * question), so a tip closed without "Verstanden" comes again.
 */
fun Modifier.tipTarget(tip: Tip, triggered: Boolean = true, key: Any? = null): Modifier = composed {
    val controller = LocalTipController.current ?: return@composed Modifier
    val target = remember(controller, tip, key) { TipController.Target(BringIntoViewRequester(), key) }
    SideEffect { target.triggered = triggered }
    DisposableEffect(controller, tip, target) {
        controller.register(tip, target)
        onDispose { controller.unregister(tip, target) }
    }
    Modifier
        .bringIntoViewRequester(target.requester)
        .onGloballyPositioned { coordinates ->
            val full = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
            // Clipped by the parents (e.g. a scrolled column): smaller if partly out of view.
            val visible = coordinates.boundsInRoot()
            target.bounds = full
            target.fullyVisible = coordinates.isAttached && full.width > 0f &&
                visible.width >= full.width - 1f && visible.height >= full.height - 1f
        }
}

/**
 * No tip while this is in the composition (a dialog, a running round, …), except the tips in
 * [allow] that are placed there on purpose.
 */
@Composable
fun BlockTips(allow: Set<Tip> = emptySet()) {
    val controller = LocalTipController.current ?: return
    DisposableEffect(controller, allow) {
        val token = controller.block(allow)
        onDispose { controller.unblock(token) }
    }
}

/** Waits this long between two looks at the target; it must be still and fully visible twice in a row. */
private const val SETTLE_STEP_MS = 300L
private const val FADE_MS = 200
private const val SCRIM_ALPHA = 0.6f
private val HOLE_MARGIN = 6.dp
private val HOLE_CORNER = 16.dp
private val SIDE_MARGIN = 16.dp
private val CARD_MAX_WIDTH = 480.dp
private val POINTER_WIDTH = 20.dp
private val POINTER_HEIGHT = 10.dp

/**
 * The spotlight over the whole app: dims the screen, cuts out the target and shows the card of
 * the most important due tip, one at a time. [onAction]: the second button of a tip (if it has
 * one) after it was marked done.
 */
@Composable
fun TipHost(controller: TipController, settings: AppSettings, onAction: (Tip) -> Unit) {
    val tips = settings.tips.collectAsState()
    val onePerVisit = settings.tipsOnePerVisit.collectAsState()
    val candidate by remember(controller) {
        derivedStateOf {
            val open = controller.targets.filterValues { !it.closed }
            TipState.next(tips.value, open.mapValues { it.value.triggered }) { tip ->
                controller.blocked(tip) || !controller.visit.allows(tip, tips.value.done, onePerVisit.value) ||
                    controller.otherTipInQuestion(tip)
            }
        }
    }
    var shown by remember { mutableStateOf<Tip?>(null) }
    LaunchedEffect(candidate) {
        shown = null
        val tip = candidate ?: return@LaunchedEffect
        val target = controller.targets[tip] ?: return@LaunchedEffect
        target.requester.bringIntoView()
        // Only once the screen is still: nothing may move under an open tip.
        var last: Rect? = null
        while (true) {
            delay(SETTLE_STEP_MS)
            val bounds = target.bounds
            if (target.fullyVisible && bounds != null && bounds == last) break
            last = bounds
        }
        shown = tip
        controller.visit.record(tip, tips.value.done)
        controller.recordInQuestion(tip, target)
    }
    val tip = shown?.takeIf { it == candidate }
    val target = tip?.let { controller.targets[it] }
    val visible = tip != null && target != null && target.fullyVisible
    SideEffect { controller.shown = if (visible) tip else null }

    // The last shown tip stays drawn while it fades out.
    var lastTip by remember { mutableStateOf<Tip?>(null) }
    var lastHole by remember { mutableStateOf(Rect.Zero) }
    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    // How far the window content is moved on the screen (e.g. panned up for the keyboard); 0 normally.
    var windowShift by remember { mutableStateOf(0f) }
    if (visible) {
        lastTip = tip
        target?.bounds?.let { lastHole = it.translate(-hostOffset) }
    }
    val close = { target?.closed = true }
    BackHandler(enabled = visible, onBack = close)

    val fade = if (animationsEnabled(LocalContext.current)) FADE_MS else 0
    Box(
        modifier = Modifier.fillMaxSize().onGloballyPositioned {
            hostOffset = it.positionInRoot()
            windowShift = it.positionInWindow().y - hostOffset.y
        }
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(fade)), exit = fadeOut(tween(fade))) {
            val shownTip = lastTip ?: return@AnimatedVisibility
            TipOverlay(
                content = TipTexts.content(
                    shownTip,
                    verbs = settings.quizMode.value == QuizMode.VERBS,
                    threeVerbForms = settings.verbFormMode.value == VerbFormMode.THREE
                ),
                // A tip in a question leaves the focus (and so the keyboard) in the input field.
                takeFocus = shownTip !in Tip.IN_QUESTION,
                hole = lastHole,
                windowShift = windowShift,
                onUnderstood = { settings.tipUnderstood(shownTip) },
                onSecondary = {
                    settings.tipUnderstood(shownTip)
                    onAction(shownTip)
                },
                onClose = close
            )
        }
    }
}

@Composable
private fun TipOverlay(
    content: TipContent,
    takeFocus: Boolean,
    hole: Rect,
    /** See [TipHost]: the card is kept inside the part of the overlay that is on the screen. */
    windowShift: Float,
    onUnderstood: () -> Unit,
    onSecondary: () -> Unit,
    onClose: () -> Unit
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val insets = WindowInsets.safeDrawing
    val focus = remember { FocusRequester() }
    LaunchedEffect(content, takeFocus) { if (takeFocus) runCatching { focus.requestFocus() } }
    // Set during layout: whether the card sits below the target (pointer on top, pointing up).
    var below by remember { mutableStateOf(true) }
    Layout(
        content = {
            // The dimmed screen with the target cut out; a tap closes the tip without "Verstanden".
            // It covers the target too, so the target cannot be tapped through the spotlight.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TipTags.SCRIM)
                    .clearAndSetSemantics {}
                    .pointerInput(onClose) { detectTapGestures { onClose() } }
            ) {
                val margin = HOLE_MARGIN.toPx()
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(hole.inflate(margin), CornerRadius(HOLE_CORNER.toPx())))
                }
                drawPath(path, Color.Black.copy(alpha = SCRIM_ALPHA))
            }
            Box(modifier = Modifier.testTag(TipTags.HOLE))
            Canvas(modifier = Modifier.clearAndSetSemantics {}) {
                val path = Path().apply {
                    if (below) {
                        moveTo(0f, size.height)
                        lineTo(size.width / 2, 0f)
                        lineTo(size.width, size.height)
                    } else {
                        moveTo(0f, 0f)
                        lineTo(size.width / 2, size.height)
                        lineTo(size.width, 0f)
                    }
                    close()
                }
                drawPath(path, cardColor)
            }
            Surface(
                modifier = Modifier
                    .testTag(TipTags.CARD)
                    .semantics {
                        paneTitle = content.title
                        liveRegion = LiveRegionMode.Polite
                    }
                    .then(if (takeFocus) Modifier.focusRequester(focus).focusable() else Modifier),
                shape = MaterialTheme.shapes.large,
                color = cardColor,
                shadowElevation = 8.dp
            ) {
                TipCard(content, onUnderstood, onSecondary)
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = HOLE_MARGIN.toPx()
        val cutout = hole.inflate(margin)
        val scrim = measurables[0].measure(Constraints.fixed(width, height))
        val holeBox = measurables[1].measure(
            Constraints.fixed(cutout.width.roundToInt().coerceAtLeast(0), cutout.height.roundToInt().coerceAtLeast(0))
        )
        val pointerWidth = POINTER_WIDTH.roundToPx()
        val pointerHeight = POINTER_HEIGHT.roundToPx()
        val pointer = measurables[2].measure(Constraints.fixed(pointerWidth, pointerHeight))
        val cardWidth = minOf(width - 2 * SIDE_MARGIN.roundToPx(), CARD_MAX_WIDTH.roundToPx()).coerceAtLeast(0)
        val card = measurables[3].measure(Constraints(minWidth = cardWidth, maxWidth = cardWidth, maxHeight = height))

        // The visible area in overlay coordinates: below the status bar, above the keyboard or the
        // navigation bar, corrected by a shift of the window (otherwise the card may end up off screen).
        val shift = windowShift.roundToInt()
        val top = insets.getTop(this) + SIDE_MARGIN.roundToPx() - shift
        val bottom = maxOf(top, height - insets.getBottom(this) - SIDE_MARGIN.roundToPx() - shift)
        val spaceBelow = bottom - cutout.bottom - pointerHeight
        val spaceAbove = cutout.top - top - pointerHeight
        // The side where it fits; if it fits on neither, the larger one (the card then covers as
        // little of the target as possible, but is always fully visible).
        val placeBelow = when {
            spaceBelow >= card.height -> true
            spaceAbove >= card.height -> false
            else -> spaceBelow >= spaceAbove
        }
        val cardY = if (placeBelow) {
            (cutout.bottom.roundToInt() + pointerHeight).coerceAtMost(bottom - card.height).coerceAtLeast(top)
        } else {
            (cutout.top.roundToInt() - pointerHeight - card.height).coerceAtMost(bottom - card.height).coerceAtLeast(top)
        }
        val cardX = (width - cardWidth) / 2
        // The pointer only where the card does not cover the target.
        val pointerFits = if (placeBelow) cardY >= cutout.bottom else cardY + card.height <= cutout.top
        val corner = HOLE_CORNER.roundToPx()
        val pointerX = (cutout.center.x.roundToInt() - pointerWidth / 2)
            .coerceIn(cardX + corner, (cardX + cardWidth - corner - pointerWidth).coerceAtLeast(cardX + corner))
        val pointerY = if (placeBelow) cardY - pointerHeight else cardY + card.height
        below = placeBelow
        layout(width, height) {
            scrim.place(0, 0)
            holeBox.place(cutout.left.roundToInt(), cutout.top.roundToInt())
            if (pointerFits) pointer.place(pointerX, pointerY)
            card.place(cardX, cardY)
        }
    }
}

@Composable
private fun TipCard(content: TipContent, onUnderstood: () -> Unit, onSecondary: () -> Unit) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(content.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
            Text(
                content.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }
        content.lead?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content.lines.forEach { line ->
                Text(if (content.bullets) "• $line" else line, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            content.secondary?.let { label -> OutlinedButton(onClick = onSecondary) { Text(label) } }
            Button(onClick = onUnderstood) { Text(TipTexts.UNDERSTOOD) }
        }
    }
}
