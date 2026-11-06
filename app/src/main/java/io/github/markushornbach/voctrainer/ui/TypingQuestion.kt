package io.github.markushornbach.voctrainer.ui

import android.text.InputType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.learning.AlmostKind
import io.github.markushornbach.voctrainer.learning.AnswerChecker
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.TypingTask
import io.github.markushornbach.voctrainer.ui.theme.LocalAnswerColors

const val EMPTY_INPUT_HINT = "Erst ein Wort eintippen"
const val COPY_INSTRUCTION = "Tippe das markierte Wort ab."
// One line, so it fits the feedback slot below the reason and the correct word of "Fast richtig".
const val COPY_MISMATCH_HINT = "Noch nicht ganz – schreib genau ab."
const val CHOICE_BUTTON = "Mit Vorschlägen"

/** Width share of "Prüfen" next to "Mit Vorschlägen" (1). */
private const val CHECK_BUTTON_WEIGHT = 1.3f
const val CHOICE_FALLBACK_NOTE = "Wort fällt auf ‚am Üben‘ zurück"

/** "Auch richtig! Gesucht war: …" */
fun alsoCorrectText(english: String): String = "Auch richtig! Gesucht war: $english"

// The reasons of "Fast richtig" below the frame; "Fast richtig" itself is shown by the result banner.
const val ALMOST_TYPO_TEXT = "Kleiner Tippfehler"
const val ALMOST_AMERICAN_TEXT = "Im Lehrbuch britisch"
const val ALMOST_OTHER_PREFIX = "Gesucht war: "
const val ALMOST_COPY_INSTRUCTION = "Tippe das Wort einmal richtig ab."

/** Before the correct word after a wrong answer or "Fast richtig". */
const val SOLUTION_LABEL = "So geht's richtig: "

private val leadingTo = Regex("""^(\(to\)|to)\s+""", RegexOption.IGNORE_CASE)

/**
 * [key] (lower case, as compared) in the spelling of [original] if that is the same text, so a
 * capital letter stays ("Monday"); the marked positions then still fit. A leading "to " is dropped.
 */
fun shownLike(original: String?, key: String): String {
    val text = original?.trim()?.replace(leadingTo, "")?.trim() ?: return key
    return if (text.length == key.length && AnswerChecker.typedKey(text) == key) text else key
}

/**
 * The note on "Mit Vorschlägen": only for a word on level 4 or 5 and only on its first question in the
 * round (a repetition never changes the level).
 */
fun showsChoiceFallbackNote(level: Int, isRepeat: Boolean): Boolean =
    !isRepeat && level > Leitner.CHOICE_MAX_TYPEABLE

/**
 * The top part of a typing question, below the German meanings: the frame with the gap filling as typed
 * and the fixed feedback slot. Neither changes its height after the start of the question.
 */
@Composable
fun TypingFeedback(
    state: QuizRoundState.Asking,
    task: TypingTask,
    typing: TypingState,
    /** Compact layout: for the lines shown in the feedback slot, without the rest of its reserved height. */
    linesModifier: Modifier = Modifier
) {
    TypingFrame(task, typing)
    // One slot of fixed height for all information, so nothing moves when it appears.
    FeedbackSlot(state, task, typing, linesModifier = linesModifier)
}

/**
 * The bottom part of a typing question, kept above the keyboard: the input field and "Prüfen" with the
 * delayed "Mit Vorschlägen"; after the answer the result banner takes the place of "Mit Vorschlägen". The options of "Mit Vorschlägen" are shown by the quiz screen itself.
 */
@Composable
fun TypingControls(
    state: QuizRoundState.Asking,
    typing: TypingState,
    vibration: Boolean,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onChoose: () -> Unit,
    /** For "Mit Vorschlägen" while it can be tapped (a tip target). */
    choiceModifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember(state.question) { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val answerVibration = remember(context) { AnswerVibration(context) }

    // Keyboard opens with the question. The field stays focused (input ignored once answered) until the next
    // question, so the keyboard does not close and reopen between typing questions.
    LaunchedEffect(state.question) {
        focus.requestFocus()
        keyboard?.show()
    }
    // Only the wrong typed answer vibrates, not "Fast richtig" and not a mistake while copying.
    LaunchedEffect(state.question, typing.phase) {
        if (vibration && typing.phase == TypingPhase.COPYING && typing.almost == null) answerVibration.wrong()
    }

    val answerColors = LocalAnswerColors.current
    // The field always shows what was typed; green once it is right (also the copied word).
    val inputRight = typing.phase == TypingPhase.CORRECT || typing.phase == TypingPhase.ALSO_CORRECT ||
        typing.phase == TypingPhase.COPIED
    val inputStyle = MaterialTheme.typography.titleLarge.let {
        if (inputRight) it.copy(color = answerColors.onCorrectContainer, fontWeight = FontWeight.Bold) else it
    }
    val compact = LocalCompactHeight.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = compactSpacing(compact, normal = 12.dp, small = 8.dp)
    ) {
        if (typing.phase != TypingPhase.CHOICE) {
            NoSuggestionsTextInput {
                OutlinedTextField(
                    value = typing.input,
                    // Locked after the answer by ignoring input, not by readOnly/enabled: both would end the
                    // text-input session and close the keyboard.
                    onValueChange = { if (typing.inputOpen) onInput(it) },
                    singleLine = true,
                    isError = typing.copyMismatch,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (typing.inputOpen) onSubmit() }),
                    textStyle = inputStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(QuizTags.INPUT)
                        .focusRequester(focus)
                        .semantics { if (!typing.inputOpen) disabled() }
                )
            }
            // Side by side, so both fit above the keyboard: "Prüfen" large on the left, the delayed "Mit Vorschlägen"
            // smaller on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = compactSpacing(compact, normal = 12.dp, small = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = typing.inputOpen,
                    modifier = Modifier
                        .weight(CHECK_BUTTON_WEIGHT)
                        .heightIn(min = if (compact) 48.dp else 56.dp)
                        .testTag(QuizTags.CHECK)
                ) {
                    Text("Prüfen", style = MaterialTheme.typography.titleMedium)
                }
                // Always laid out, invisible until it appears: its height (two lines with a large font) is reserved,
                // so neither "Prüfen" nor the field moves when it appears or after the answer. After the answer the
                // result banner shows in its place (it costs no extra height above the keyboard).
                val choiceVisible = typing.phase == TypingPhase.TYPING && typing.showsChoiceButton
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    OutlinedButton(
                        onClick = {
                            keyboard?.hide()
                            onChoose()
                        },
                        enabled = choiceVisible,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .then(
                                if (choiceVisible) Modifier.testTag(QuizTags.CHOICE).then(choiceModifier)
                                else Modifier.alpha(0f).clearAndSetSemantics { }
                            )
                    ) {
                        // Two lines on narrow screens or with a large font, never cut off.
                        Text(
                            CHOICE_BUTTON,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                    answerResult(state)?.let { ResultBanner(it, maxLines = 2) }
                }
            }
        }
    }
}

/**
 * The frame with the gap: the input while typing, then always the typed answer: green if correct, red if
 * wrong, after "Fast richtig" calm with the wrong letters in red. The correct word is shown below it in the
 * feedback slot. Its height is reserved for the frame with the solution from the start.
 */
@Composable
private fun TypingFrame(task: TypingTask, typing: TypingState) {
    val answerColors = LocalAnswerColors.current
    val scheme = MaterialTheme.colorScheme
    val almost = typing.almost
    val typed = typing.typedAnswer.orEmpty()
    val gap: AnnotatedString = when (typing.phase) {
        TypingPhase.TYPING, TypingPhase.CHOICE -> styled(TypingTask.gap(typing.input), scheme.primary, Color.Unspecified)
        TypingPhase.CORRECT, TypingPhase.ALSO_CORRECT ->
            styled(typed, answerColors.onCorrectContainer, answerColors.correctContainer)
        TypingPhase.COPYING, TypingPhase.COPIED -> when {
            almost == null -> styled(typed, scheme.onErrorContainer, scheme.errorContainer)
            almost.kind != AlmostKind.TYPO_OF_OTHER -> {
                val shown = shownLike(typed, almost.given)
                buildAnnotatedString {
                    append(styled(shown, answerColors.onAlmostContainer, answerColors.almostContainer))
                    val range = almost.diff.given
                    if (!range.isEmpty() && range.last < shown.length) {
                        addStyle(
                            SpanStyle(background = scheme.errorContainer, color = scheme.onErrorContainer),
                            range.first, range.last + 1
                        )
                    }
                }
            }
            else -> styled(typed, answerColors.onAlmostContainer, answerColors.almostContainer)
        }
    }
    val solution = task.coreWord
    val style = MaterialTheme.typography.headlineSmall
    Box(contentAlignment = Alignment.Center) {
        // Invisible placeholder with the solution in the gap: keeps the height if the solution needs a second line.
        Text(
            text = frameText(task, styled(solution, Color.Unspecified, Color.Unspecified)),
            style = style,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics { }
        )
        Text(text = frameText(task, gap), style = style, textAlign = TextAlign.Center)
    }
}

/**
 * The fixed feedback slot below the frame, reserved for [FEEDBACK_SLOT_LINES] lines from the start of the
 * question, only for what the learner needs to continue: "Auch richtig! …", the correct word after a wrong
 * answer or "Fast richtig" (with its reason), the copy instruction or its mismatch hint, the empty-input
 * hint and the note on "Mit Vorschlägen" (level 4/5). A correct answer shows nothing here; the gap turns
 * green. Right or wrong itself is shown by the result banner.
 */
@Composable
private fun FeedbackSlot(
    state: QuizRoundState.Asking,
    task: TypingTask,
    typing: TypingState,
    modifier: Modifier = Modifier,
    linesModifier: Modifier = Modifier
) {
    val word = state.question.question.word
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }

    @Composable
    fun line(text: AnnotatedString, color: Color = Color.Unspecified, bold: Boolean = false) = Text(
        text = text,
        style = style,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else null,
        textAlign = TextAlign.Center
    )

    @Composable
    fun line(text: String, color: Color = Color.Unspecified, bold: Boolean = false) =
        line(AnnotatedString(text), color, bold)

    val lines: @Composable () -> Unit = {
        when (typing.phase) {
            TypingPhase.TYPING -> {
                if (typing.emptyHint) line(EMPTY_INPUT_HINT, scheme.error)
                if (typing.showsChoiceButton && showsChoiceFallbackNote(word.leitnerLevel, state.question.isRepeat)) {
                    Text(
                        text = "$CHOICE_BUTTON: $CHOICE_FALLBACK_NOTE",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            TypingPhase.CORRECT -> Unit
            TypingPhase.ALSO_CORRECT -> line(alsoCorrectText(word.english), bold = true)
            TypingPhase.COPYING, TypingPhase.COPIED -> {
                val almost = typing.almost
                val answerColors = LocalAnswerColors.current
                when (almost?.kind) {
                    null -> line(
                        buildAnnotatedString {
                            append(SOLUTION_LABEL)
                            append(styled(task.coreWord, answerColors.onCorrectContainer, answerColors.correctContainer))
                        }
                    )
                    AlmostKind.TYPO_OF_OTHER -> line(
                        buildAnnotatedString {
                            append(ALMOST_OTHER_PREFIX)
                            append(solutionText(word.english, task))
                        },
                        bold = true
                    )
                    AlmostKind.TYPO, AlmostKind.AMERICAN -> {
                        line(if (almost.kind == AlmostKind.TYPO) ALMOST_TYPO_TEXT else ALMOST_AMERICAN_TEXT, bold = true)
                        line(
                            buildAnnotatedString {
                                append(SOLUTION_LABEL)
                                // The corrected letters underlined.
                                append(
                                    marked(
                                        shownLike(word.coreWord, almost.expected), almost.diff.expected,
                                        answerColors.correctContainer, answerColors.onCorrectContainer,
                                        whole = true
                                    )
                                )
                            }
                        )
                    }
                }
                if (typing.phase == TypingPhase.COPYING) {
                    when {
                        typing.copyMismatch -> line(COPY_MISMATCH_HINT, scheme.error)
                        almost != null && almost.kind != AlmostKind.TYPO_OF_OTHER -> line(ALMOST_COPY_INSTRUCTION)
                        else -> line(COPY_INSTRUCTION)
                    }
                }
            }
            TypingPhase.CHOICE -> Unit
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(QuizTags.FEEDBACK)
            .heightIn(min = lineHeight * FEEDBACK_SLOT_LINES + FEEDBACK_SLOT_SPACING * (FEEDBACK_SLOT_LINES - 1)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FEEDBACK_SLOT_SPACING)
    ) {
        if (LocalCompactHeight.current) {
            Column(
                modifier = linesModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FEEDBACK_SLOT_SPACING)
            ) { lines() }
        } else {
            lines()
        }
    }
}

/** Lines reserved for the feedback slot: the longest case is "Fast richtig" (reason, correct word, instruction). */
private const val FEEDBACK_SLOT_LINES = 3
private val FEEDBACK_SLOT_SPACING = 2.dp

private fun styled(text: String, color: Color, background: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = color, background = background, fontWeight = FontWeight.Bold)) { append(text) }
}

/**
 * [text] with the letters in [range] highlighted (nothing if the range is empty or does not fit). With
 * [whole], the whole word gets the colours and the letters in [range] are underlined.
 */
private fun marked(text: String, range: IntRange, background: Color, color: Color, whole: Boolean = false): AnnotatedString =
    buildAnnotatedString {
        if (whole) append(styled(text, color, background)) else append(text)
        if (!range.isEmpty() && range.last < text.length) {
            val style = if (whole) SpanStyle(textDecoration = TextDecoration.Underline)
            else SpanStyle(background = background, color = color, fontWeight = FontWeight.Bold)
            addStyle(style, range.first, range.last + 1)
        }
    }

private fun frameText(task: TypingTask, gap: AnnotatedString): AnnotatedString =
    buildAnnotatedString {
        append(task.before)
        append(gap)
        append(task.after)
    }

/** The full English entry with the core word highlighted. */
private fun solutionText(english: String, task: TypingTask): AnnotatedString = buildAnnotatedString {
    val core = english.removePrefix(task.before).removeSuffix(task.after)
    if (task.before.isEmpty() && task.after.isEmpty() && !core.equals(task.coreWord, ignoreCase = true)) {
        // Core word not found in the entry: show the entry and the core word to type.
        append(english)
        append(" – ")
    } else {
        append(task.before)
    }
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
        append(if (task.before.isEmpty() && task.after.isEmpty()) task.coreWord else core)
    }
    append(task.after)
}

/**
 * Compose cannot ask for "no suggestions"; Gboard only hides them reliably for the visible
 * password variation, which shows the text as typed (unlike a password field). The keyboard
 * options of the field (Done action, no capitalization) stay.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NoSuggestionsTextInput(content: @Composable () -> Unit) {
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            val noSuggestions = PlatformTextInputMethodRequest { outAttributes ->
                val connection = request.createInputConnection(outAttributes)
                outAttributes.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                connection
            }
            nextHandler.startInputMethod(noSuggestions)
        }
    }
    InterceptPlatformTextInput(interceptor = interceptor, content = content)
}
