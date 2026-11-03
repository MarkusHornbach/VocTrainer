package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.Reach
import io.github.markushornbach.voctrainer.learning.StreakInfo
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.ui.theme.KindColor
import io.github.markushornbach.voctrainer.ui.theme.LevelKindColors
import io.github.markushornbach.voctrainer.ui.theme.LocalBadgeTierColors
import io.github.markushornbach.voctrainer.ui.theme.LocalLevelKindColors

/** Test tag of the scrollable content of every info box. */
const val INFO_CONTENT_TAG = "info_content"

/** Reusable info box: title, scrollable content and "OK". */
@Composable
fun InfoDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BlockTips()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ScrollColumn(
                modifier = Modifier.testTag(INFO_CONTENT_TAG),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

/** The ⓘ that opens an info box; IconButton gives the 48 dp touch target. */
@Composable
fun InfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = INFO_BUTTON_DESCRIPTION,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

const val INFO_BUTTON_DESCRIPTION = "Erklärung anzeigen"

/** Marks something that cannot be reached yet; neutral colour, never red or green. */
@Composable
fun LockIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    description: String? = "noch nicht erreichbar",
    tint: Color = MaterialTheme.colorScheme.outline
) {
    Icon(
        painterResource(R.drawable.ic_lock),
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/** Lock icon in front of a short text. */
@Composable
fun LockedNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LockIcon(modifier = Modifier.padding(top = 2.dp), description = null)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What an info page shows of the learner's current state; the defaults fit the help page without data. */
data class InfoPageData(
    val figureStage: Int = 0,
    val reach: Reach = Reach.EMPTY,
    val streak: StreakInfo? = null,
    val goal: Int = AppSettings.DEFAULT_DAILY_GOAL,
    val mode: QuizMode = QuizMode.VOCABULARY,
    /** "Verbformen" is "3 Formen" ("Verben üben"). */
    val threeVerbForms: Boolean = false
)

/** Opens [page] with [data] (help page, test tools and the ⓘ that have no own data). */
@Composable
fun InfoPageDialog(page: InfoPage, data: InfoPageData, onDismiss: () -> Unit) {
    when (page) {
        InfoPage.SELECTION -> SelectionInfoDialog(data.mode, onDismiss)
        InfoPage.LEVELS -> LevelsInfoDialog(QuizMode.VOCABULARY, data.threeVerbForms, onDismiss)
        InfoPage.VERBS -> LevelsInfoDialog(QuizMode.VERBS, data.threeVerbForms, onDismiss)
        InfoPage.POINTS -> PointsInfoDialog(restartNote = true, onDismiss = onDismiss)
        InfoPage.FIGURE -> FigureInfoDialog(data.figureStage, data.reach, onDismiss)
        InfoPage.BADGES -> BadgesInfoDialog(onDismiss)
        InfoPage.STREAK -> StreakInfoDialog(data.streak, data.goal, onDismiss)
        InfoPage.DETAILS -> DetailsInfoDialog(onDismiss)
        InfoPage.BOOKS -> BooksInfoDialog(onDismiss)
        InfoPage.RESTART -> RestartInfoDialog(onDismiss)
        InfoPage.PRACTICE_SETTINGS -> PracticeSettingsInfoDialog(onDismiss)
    }
}

// ---- Building blocks: short lines, blocks and tables ----

/** Short lines, each with its optional muted note. */
@Composable
private fun InfoLines(lines: List<InfoLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            Column {
                Text(line.text, style = MaterialTheme.typography.bodyMedium)
                line.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BlockTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun InfoBlockView(block: InfoBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.title?.let { BlockTitle(it) }
        InfoLines(block.lines)
    }
}

/** A two-column table; both columns wrap, so nothing scrolls sideways with a large font. */
@Composable
private fun InfoTable(rows: List<InfoRow>, header: InfoRow? = null) {
    Column {
        if (header != null) {
            InfoTableRow(header, bold = true)
            HorizontalDivider()
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoTableRow(row, bold = false)
        }
    }
}

@Composable
private fun InfoTableRow(row: InfoRow, bold: Boolean) {
    val weight = if (bold) FontWeight.Bold else null
    val style = MaterialTheme.typography.bodyMedium
    val color = if (row.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = STAGE_ROW_HEIGHT)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(row.label, style = style, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.value, style = style, fontWeight = weight, color = color)
            row.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- I1 / I9 "Was bedeuten die Level?" ----

/**
 * "Was bedeuten die Level?": in vocabulary mode the staircase of the question kinds, then the level
 * rules; in the verb mode the block "Verben üben" and the coloured level group.
 */
@Composable
fun LevelsInfoDialog(mode: QuizMode, threeVerbForms: Boolean, onDismiss: () -> Unit) {
    val blocks = InfoTexts.levelBlocks(mode, threeVerbForms)
    InfoDialog(InfoTexts.LEVELS_TITLE, onDismiss) {
        when (mode) {
            QuizMode.VOCABULARY -> {
                AskStaircase(InfoTexts.askSteps())
                blocks.forEach { InfoBlockView(it) }
            }
            QuizMode.VERBS -> {
                blocks.forEach { InfoBlockView(it) }
                LevelGroups(InfoTexts.levelRows(mode, threeVerbForms))
            }
        }
    }
}

/** Levels with name, grouped by question type in the level-kind colours. */
@Composable
private fun LevelGroups(rows: List<LevelRow>) {
    val colors = LocalLevelKindColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoTexts.levelGroups(rows).forEach { group ->
            val color = colors.of(group.kind)
            Surface(color = color.container, contentColor = color.onContainer, shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    // The label carries the meaning; the colour only groups the levels.
                    Text(group.kind.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    group.rows.forEach { row ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Level ${row.level}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
                            Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                                if (row.locked) LockIcon()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Three steps, lowest on the left; on a narrow box three stacked rows with a growing indent instead
 * (still coloured, still rising). The font size is always the same (see [FixedUiScale]).
 */
@Composable
internal fun AskStaircase(steps: List<AskStep>) {
    val colors = LocalLevelKindColors.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= STAIRCASE_MIN_WIDTH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                steps.forEachIndexed { index, step ->
                    AskStepBox(
                        step,
                        colors.of(step.kind),
                        Modifier
                            .weight(1f)
                            .heightIn(min = STEP_BASE_HEIGHT + STEP_RISE * index)
                    )
                }
            }
        } else {
            // Highest step on top, so it still reads as a staircase.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.indices.reversed().forEach { index ->
                    AskStepBox(
                        steps[index],
                        colors.of(steps[index].kind),
                        Modifier
                            .padding(start = STEP_INDENT * index)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AskStepBox(step: AskStep, color: KindColor, modifier: Modifier) {
    Surface(
        color = color.container,
        contentColor = color.onContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(step.direction, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(step.form, style = MaterialTheme.typography.bodySmall)
            Text(step.levels, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

private val STAIRCASE_MIN_WIDTH = 240.dp
private val STEP_BASE_HEIGHT = 84.dp
private val STEP_RISE = 24.dp
private val STEP_INDENT = 24.dp

internal fun LevelKindColors.of(kind: LevelQuestionKind): KindColor = when (kind) {
    LevelQuestionKind.EN_TO_DE_CHOOSE -> enToDeChoose
    LevelQuestionKind.DE_TO_EN_CHOOSE, LevelQuestionKind.VERB_SIMPLE_PAST_CHOOSE,
    LevelQuestionKind.VERB_THREE_FORMS_CHOOSE -> deToEnChoose
    LevelQuestionKind.DE_TO_EN_TYPE -> deToEnType
}

// ---- I4 "So bekommst du Punkte" ----

/** "So bekommst du Punkte": one table "Wofür → Punkte". [restartNote]: on the help page, also the one exception. */
@Composable
fun PointsInfoDialog(restartNote: Boolean = false, onDismiss: () -> Unit) {
    InfoDialog(InfoTexts.POINTS_TITLE, onDismiss) {
        InfoTable(InfoTexts.pointsRows(), header = InfoTexts.POINTS_HEADER)
        InfoLines(InfoTexts.pointsLines(restartNote))
    }
}

// ---- I3 "So wächst dein Avatar" ----

private val STAGE_ROW_HEIGHT = 40.dp

/** About 5½ rows, so it is clear that the table scrolls. */
private val STAGE_TABLE_HEIGHT = STAGE_ROW_HEIGHT * 5.5f
// Room for "nächste Stufe" in one line; wider would make the "Punkte" header wrap on a narrow phone.
private val STATUS_WIDTH = 88.dp

/** "So wächst dein Avatar": short lines and the table of all stages, scrolled to the next stage. */
@Composable
fun FigureInfoDialog(currentStage: Int, reach: Reach, onDismiss: () -> Unit) {
    val rows = InfoTexts.stageRows(currentStage, reach)
    InfoDialog(InfoTexts.FIGURE_TITLE, onDismiss) {
        InfoLines(InfoTexts.figureLines())
        Column {
            StageTableRow(
                stage = "Stufe",
                points = "Punkte",
                sureWords = "gelernte Wörter",
                bold = true,
                background = null
            ) {}
            HorizontalDivider()
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = InfoTexts.stageScrollIndex(currentStage))
            LazyColumn(state = listState, modifier = Modifier.height(STAGE_TABLE_HEIGHT)) {
                items(rows, key = { it.stage }) { row ->
                    StageTableRow(
                        stage = row.stage.toString(),
                        points = PracticeTexts.formatNumber(row.points),
                        sureWords = if (row.sureWords == 0) "–" else row.sureWords.toString(),
                        bold = row.isNext,
                        background = if (row.isNext) MaterialTheme.colorScheme.primaryContainer else null
                    ) {
                        when {
                            row.reached -> Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = "erreicht",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            row.isNext -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val markerStyle = MaterialTheme.typography.bodyMedium
                                // One line: next to a lock or with a large font the marker gets a little smaller.
                                Text(
                                    InfoTexts.NEXT_STAGE_MARKER,
                                    style = markerStyle,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = markerStyle.fontSize),
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (row.locked) LockIcon(size = 16.dp)
                            }
                            row.locked -> LockIcon()
                        }
                    }
                }
            }
        }
        if (rows.any { it.locked }) LockedNote(InfoTexts.lockedStagesNote(reach.possibleSureWords))
    }
}

@Composable
private fun StageTableRow(
    stage: String,
    points: String,
    sureWords: String,
    bold: Boolean,
    background: Color?,
    status: @Composable () -> Unit
) {
    val weight = if (bold) FontWeight.Bold else null
    val style = MaterialTheme.typography.bodyMedium
    val color = if (background != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(STAGE_ROW_HEIGHT)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stage, style = style, fontWeight = weight, color = color, modifier = Modifier.weight(0.8f))
        Text(points, style = style, fontWeight = weight, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(sureWords, style = style, fontWeight = weight, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
        Box(modifier = Modifier.width(STATUS_WIDTH), contentAlignment = Alignment.Center) { status() }
    }
}

// ---- I5 "Deine Serie" ----

/**
 * "Deine Serie": daily goal, streak, joker and longest streak ([streak]); without loaded values the
 * table with this device's [goal] (no joker state for this week, no longest streak).
 */
@Composable
fun StreakInfoDialog(streak: StreakInfo?, goal: Int, onDismiss: () -> Unit) {
    InfoDialog(PracticeTexts.STREAK_TITLE, onDismiss) {
        InfoTable(InfoTexts.streakRows(streak?.goalToday ?: goal, streak?.jokerUsedThisWeek, streak?.longest))
        InfoLines(InfoTexts.streakLines())
    }
}

// ---- I6 "Abzeichen" ----

/** "Abzeichen": the thresholds of every series per tier, the tier colours as dots in the header. */
@Composable
fun BadgesInfoDialog(onDismiss: () -> Unit) {
    val tierColors = LocalBadgeTierColors.current
    InfoDialog(InfoPage.BADGES.title, onDismiss) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(BADGE_LABEL_WEIGHT))
                InfoTexts.badgeTiers().forEachIndexed { index, tier ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(tierColors[index], CircleShape)
                        )
                        Text(tier, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
            HorizontalDivider()
            InfoTexts.badgeRows().forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = STAGE_ROW_HEIGHT)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(BADGE_LABEL_WEIGHT))
                    row.thresholds.forEach {
                        Text(
                            PracticeTexts.formatNumber(it),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        InfoLines(InfoTexts.badgeLines())
    }
}

private const val BADGE_LABEL_WEIGHT = 1.6f

// ---- I7 "Vokabeln auswählen" / "Verben auswählen" ----

@Composable
fun SelectionInfoDialog(mode: QuizMode, onDismiss: () -> Unit) {
    InfoDialog(InfoTexts.selectionTitle(mode), onDismiss) {
        InfoTable(InfoTexts.selectionRows())
        InfoLines(InfoTexts.selectionLines())
    }
}

// ---- I8 "Zeichen in „Details“" ----

/** Generated from [RoundDetailTexts], so the page and the list never differ. */
@Composable
fun DetailsInfoDialog(onDismiss: () -> Unit) {
    InfoDialog(InfoPage.DETAILS.title, onDismiss) {
        InfoTable(InfoTexts.detailSymbolRows(), header = InfoTexts.DETAILS_SYMBOL_HEADER)
        InfoTable(InfoTexts.detailReasonRows(), header = InfoTexts.DETAILS_REASON_HEADER)
    }
}

// ---- I10 "Bücher und Sicherung", I11 "Buch neu beginnen", I12 "Tagesziel & Erinnerung" ----

@Composable
fun BooksInfoDialog(onDismiss: () -> Unit) {
    InfoDialog(InfoPage.BOOKS.title, onDismiss) {
        InfoTable(InfoTexts.bookRows(), header = InfoTexts.BOOKS_HEADER)
        InfoLines(InfoTexts.bookLines())
    }
}

/** Two columns: what is reset and what stays; the confirmation dialogs are separate. */
@Composable
fun RestartInfoDialog(onDismiss: () -> Unit) {
    InfoDialog(InfoPage.RESTART.title, onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RestartColumn(InfoTexts.RESTART_RESET_TITLE, InfoTexts.restartResetLines(), Modifier.weight(1f))
            RestartColumn(InfoTexts.RESTART_KEEP_TITLE, InfoTexts.restartKeepLines(), Modifier.weight(1f))
        }
        InfoLines(InfoTexts.restartLines())
    }
}

@Composable
private fun RestartColumn(title: String, lines: List<String>, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BlockTitle(title)
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun PracticeSettingsInfoDialog(onDismiss: () -> Unit) {
    InfoDialog(InfoPage.PRACTICE_SETTINGS.title, onDismiss) {
        InfoLines(InfoTexts.practiceSettingsLines())
    }
}
