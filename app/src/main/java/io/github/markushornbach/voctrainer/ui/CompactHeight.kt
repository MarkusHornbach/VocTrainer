package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The question screen uses its compact layout in a window lower than this. Between a small phone (Nexus 5X:
 * 731 dp) and today's usual phones (Pixel 8a: 915 dp, Pixel 10: 923 dp).
 */
const val COMPACT_HEIGHT_THRESHOLD_DP = 760

/** The one decision for the compact question layout; depends only on the height of the window. */
fun isCompactHeight(windowHeightDp: Float): Boolean = windowHeightDp < COMPACT_HEIGHT_THRESHOLD_DP

/** Whether the question screen is compact ([isCompactHeight]); provided once by [VocTrainerApp]. */
val LocalCompactHeight = staticCompositionLocalOf { false }

/**
 * [isCompactHeight] of the current window. The window keeps its size when the keyboard opens (the keyboard is only
 * an inset), so the decision never changes during a question, nor with an answer.
 */
@Composable
fun rememberIsCompactHeight(): Boolean {
    val heightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current.density
    return remember(heightPx, density) { isCompactHeight(heightPx / density) }
}

/** The position a compact question scrolls to after a wrong typed answer: the feedback lines just in view. */
internal fun compactCopyScrollTarget(current: Int, linesBottom: Float, viewportBottom: Float, max: Int): Int =
    (current + (linesBottom - viewportBottom).toInt()).coerceIn(0, max)

/** Set on the upper part of a compact question; its value says whether the fade edge is shown right now. */
internal val CompactFadeEdgeKey = SemanticsPropertyKey<Boolean>("CompactFadeEdge")
private var SemanticsPropertyReceiver.compactFadeEdge by CompactFadeEdgeKey

private val FADE_EDGE_HEIGHT = 24.dp

/**
 * The bottom edge of the upper part fades out while more content lies below: a hint that it scrolls. Drawn over the
 * content only, so nothing moves.
 */
internal fun Modifier.compactFadeEdge(scroll: ScrollState): Modifier = this
    .semantics { compactFadeEdge = scroll.canScrollForward }
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        if (scroll.canScrollForward) {
            val height = FADE_EDGE_HEIGHT.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black),
                    startY = size.height - height,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - height),
                blendMode = BlendMode.DstOut
            )
        }
    }

/**
 * In the compact layout icon buttons take 40 dp of height instead of 48 dp. Compose still accepts touches in a
 * 48 dp target around them. Without [compact] the [content] is untouched.
 */
@Composable
internal fun SlimIconButtons(compact: Boolean, content: @Composable () -> Unit) {
    if (compact) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = content)
    } else {
        content()
    }
}

/** Spacing of the question screen, by layout; the normal values are those of the normal layout. */
internal fun compactSpacing(compact: Boolean, normal: Dp, small: Dp): Arrangement.HorizontalOrVertical =
    Arrangement.spacedBy(if (compact) small else normal)
