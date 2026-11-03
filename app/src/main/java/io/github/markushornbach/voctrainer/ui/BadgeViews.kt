package io.github.markushornbach.voctrainer.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.BadgeKey
import io.github.markushornbach.voctrainer.learning.BadgeProgress
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import io.github.markushornbach.voctrainer.ui.theme.LocalBadgeTierColors

@DrawableRes
private fun BadgeSeries.icon(): Int = when (this) {
    BadgeSeries.STREAK -> R.drawable.ic_flame
    BadgeSeries.SURE_WORDS -> R.drawable.ic_shield
}

/** Colour of [tier] 1..4; grey without a badge (0). */
@Composable
private fun tierColor(tier: Int): Color =
    if (tier == 0) MaterialTheme.colorScheme.outline else LocalBadgeTierColors.current[tier - 1]

/** "Meine Abzeichen" on "Mein Avatar": heading with ⓘ ("Abzeichen") and one row per series. */
@Composable
fun BadgeSection(rows: List<BadgeProgress>, onInfo: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = BadgeTexts.TITLE, style = MaterialTheme.typography.titleMedium)
            InfoButton(onClick = onInfo, modifier = Modifier.testTag(BADGES_INFO_TAG))
        }
        rows.forEach { BadgeRow(it) }
    }
}

/** Test tag of the ⓘ next to "Meine Abzeichen". */
const val BADGES_INFO_TAG = "badges_info"

@Composable
private fun BadgeRow(row: BadgeProgress) {
    val color = tierColor(row.tier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = BadgeTexts.description(row) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (row.tier == 0) MaterialTheme.colorScheme.surfaceContainerHighest else color.copy(alpha = 0.16f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(row.series.icon()), contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(BadgeTexts.title(row), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(BadgeTexts.value(row.series, row.value), style = MaterialTheme.typography.bodyLarge)
            Text(
                BadgeTexts.next(row),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Result screen: one line per badge earned in this round. */
@Composable
fun NewBadgeLine(badge: BadgeKey, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(badge.series.icon()),
            contentDescription = null,
            tint = tierColor(badge.tier),
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = BadgeTexts.earned(badge),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(vertical = 2.dp)
        )
    }
}
