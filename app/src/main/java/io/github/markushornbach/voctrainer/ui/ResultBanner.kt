package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.ui.theme.LocalAnswerColors

/** The result of the first answer (or of an answer in the retry), as shown in the [ResultBanner]. */
enum class AnswerResult(val text: String) {
    CORRECT("Richtig!"),
    ALMOST("Fast richtig"),
    WRONG("Leider falsch")
}

/**
 * The result shown in the banner, or null before the answer and in the retry after "Nochmal versuchen".
 * Typing: correct and "Auch richtig" are [AnswerResult.CORRECT]; while copying (and after it) the banner keeps
 * the result of the typed answer. With suggestions (also after "Mit Vorschlägen"): the tapped option.
 */
fun answerResult(state: QuizRoundState.Asking): AnswerResult? {
    val typing = state.typing
    if (typing != null && typing.phase != TypingPhase.CHOICE) {
        return when (typing.phase) {
            TypingPhase.CORRECT, TypingPhase.ALSO_CORRECT -> AnswerResult.CORRECT
            TypingPhase.COPYING, TypingPhase.COPIED -> if (typing.almost != null) AnswerResult.ALMOST else AnswerResult.WRONG
            else -> null
        }
    }
    return when (state.phase) {
        AnswerPhase.CORRECT -> AnswerResult.CORRECT
        AnswerPhase.SOLUTION -> AnswerResult.WRONG
        AnswerPhase.ANSWERING, AnswerPhase.RETRY -> null
    }
}

/**
 * A compact banner, centred in a box whose height is reserved from the start of the question, so nothing moves
 * when the result appears. With suggestions it sits below the options, when typing to the right of "Prüfen" (in
 * the place of "Mit Vorschlägen", which is gone after the answer), so it needs no extra height above the keyboard.
 * Announced by TalkBack when it changes. [maxLines] 2 lets it wrap in the narrow place next to "Prüfen".
 */
@Composable
fun ResultBanner(result: AnswerResult?, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val answerColors = LocalAnswerColors.current
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleMedium
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Box(
        modifier = modifier
            .heightIn(min = lineHeight + BANNER_PADDING * 2)
            .testTag(QuizTags.RESULT)
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                if (result != null) contentDescription = result.text
            },
        contentAlignment = Alignment.Center
    ) {
        if (result == null) return@Box
        val (container, content) = when (result) {
            AnswerResult.CORRECT -> answerColors.correctContainer to answerColors.onCorrectContainer
            AnswerResult.ALMOST -> answerColors.almostContainer to answerColors.onAlmostContainer
            AnswerResult.WRONG -> scheme.errorContainer to scheme.onErrorContainer
        }
        Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium) {
            Text(
                text = result.text,
                style = style,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = BANNER_PADDING)
            )
        }
    }
}

private val BANNER_PADDING = 6.dp
