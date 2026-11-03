package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.DetailStatus
import io.github.markushornbach.voctrainer.learning.RoundDetail
import io.github.markushornbach.voctrainer.learning.RoundReport
import java.time.LocalDate

/** Readable green on the current background (the answer colours are near-black text on green). */
@Composable
private fun hintGreen(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF7FDB98) else Color(0xFF1B7A3A)

/** Test tag of the ⓘ next to "Details". */
const val DETAILS_INFO_TAG = "details_info"

/** Minimum width of the level and points columns, so the header lines up with the rows. */
private val LEVEL_COLUMN = 56.dp
private val POINTS_COLUMN = 56.dp

/**
 * "X aufgestiegen · Y zurückgefallen" (the second part only if Y ≥ 1) and the collapsed "Details" list;
 * both are counted from [details]. [verbs]: verb training (header "Verb", no points column).
 */
@Composable
internal fun RoundChanges(details: List<RoundDetail>, verbs: Boolean, modifier: Modifier = Modifier) {
    val risen = RoundReport.risen(details)
    val dropped = RoundReport.dropped(details)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (dropped >= 1) stringResource(R.string.result_risen_dropped, risen, dropped)
            else stringResource(R.string.result_risen, risen),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (details.isEmpty()) return@Column
        var open by rememberSaveable { mutableStateOf(false) }
        var showInfo by rememberSaveable { mutableStateOf(false) }
        if (showInfo) DetailsInfoDialog { showInfo = false }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = RoundDetailTexts.TITLE + if (open) " ▴" else " ▾",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { open = !open }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
            InfoButton(onClick = { showInfo = true }, modifier = Modifier.testTag(DETAILS_INFO_TAG))
        }
        if (open) {
            val today = AppClock.today()
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailHeader(verbs)
                details.forEach { DetailRow(it, today) }
            }
        }
    }
}

/** Column titles above the rows: subtle, read out once as a heading (the rows have their own descriptions). */
@Composable
private fun DetailHeader(verbs: Boolean) {
    val titles = RoundDetailTexts.header(verbs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                heading()
                contentDescription = titles.joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val style = MaterialTheme.typography.labelLarge
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(text = titles[0], style = style, color = color, modifier = Modifier.weight(1f))
        Text(
            text = titles[1], style = style, color = color, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = LEVEL_COLUMN)
        )
        titles.getOrNull(2)?.let {
            Text(
                text = it, style = style, color = color, textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = POINTS_COLUMN)
            )
        }
    }
}

/** Two lines at most: word, level status and points; then the short explanation. */
@Composable
private fun DetailRow(detail: RoundDetail, today: LocalDate) {
    val scheme = MaterialTheme.colorScheme
    val statusColor = when (detail.status) {
        DetailStatus.UP -> hintGreen()
        DetailStatus.DOWN -> scheme.error
        DetailStatus.SAME -> scheme.onSurface
    }
    val explanation = RoundDetailTexts.explanation(detail, today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = RoundDetailTexts.description(detail, today) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = detail.english,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = RoundDetailTexts.status(detail),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = LEVEL_COLUMN)
            )
            RoundDetailTexts.points(detail)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = POINTS_COLUMN)
                )
            }
        }
        if (explanation != null) {
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
