package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.StreakInfo
import io.github.markushornbach.voctrainer.ui.theme.LocalAnswerColors

/** Orange in every theme: a flame, and never mistaken for the green "correct" colour. */
private val FlameColor = Color(0xFFFF8A1F)

/** Test tags of the streak bar and its ⓘ. */
const val STREAK_BAR_TAG = "streak_bar"
const val STREAK_INFO_TAG = "streak_info"

/**
 * Compact streak bar at the top of the start screen; the bar and its ⓘ both open the info page
 * about goal, streak and joker. The ⓘ lies over a reserved slot at the end, so the bar keeps its height.
 */
@Composable
fun StreakBar(streak: StreakInfo, modifier: Modifier = Modifier) {
    var showInfo by rememberSaveable { mutableStateOf(false) }

    if (showInfo) StreakInfoDialog(streak, streak.goalToday) { showInfo = false }

    Box(modifier = modifier.fillMaxWidth().testTag(STREAK_BAR_TAG)) {
        StreakCard(streak, onClick = { showInfo = true })
        InfoButton(
            onClick = { showInfo = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .testTag(STREAK_INFO_TAG)
        )
    }
}

/** Room at the end of the bar for the ⓘ (its 48 dp touch target). */
private val INFO_SLOT = 48.dp

@Composable
private fun StreakCard(streak: StreakInfo, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = INFO_SLOT, top = 10.dp, bottom = 10.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_flame),
                contentDescription = null,
                tint = if (streak.current > 0) FlameColor else scheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (streak.current > 0) PracticeTexts.days(streak.current) else PracticeTexts.START_STREAK,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = PracticeTexts.roundsToday(streak.roundsToday, streak.goalToday),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            if (streak.jokerUsedThisWeek) {
                Icon(
                    painterResource(R.drawable.ic_shield),
                    contentDescription = "Joker diese Woche eingesetzt",
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (streak.todayReached) {
                val answerColors = LocalAnswerColors.current
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(answerColors.correctContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = "Tagesziel geschafft",
                        tint = answerColors.onCorrectContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
