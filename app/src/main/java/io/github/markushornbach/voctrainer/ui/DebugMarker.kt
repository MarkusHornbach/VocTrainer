package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.ui.theme.OnTestOrange
import io.github.markushornbach.voctrainer.ui.theme.TestOrange

/**
 * The one shared look for everything that exists only in the test app (debug build): test orange,
 * `🧪 ` before the label of a single element, a slim bar `🧪 Nur Test-App` under the title of a whole
 * screen. Only call these where the element itself is debug-only; release must look as before.
 */
object DebugMarker {
    const val PREFIX = "🧪 "
    const val BAR_TEXT = "🧪 Nur Test-App"

    /** Test tag of the bar under the title of a debug-only screen. */
    const val BAR_TAG = "debug_screen_bar"

    /** [text] with the test prefix, e.g. "🧪 Vokabel hinzufügen". */
    fun label(text: String): String = PREFIX + text
}

/** Orange outline for a debug-only element that is not a button (card, switch row). */
fun Modifier.debugOutline(): Modifier = this.border(BorderStroke(1.dp, TestOrange), RoundedCornerShape(12.dp))

/** Outlined button of the test app: orange text and outline, label with the test prefix. */
@Composable
fun DebugButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        border = BorderStroke(1.dp, TestOrange),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TestOrange)
    ) {
        Text(DebugMarker.label(label))
    }
}

/** Switch row of the test app: orange outline and title with the test prefix. */
@Composable
fun DebugSwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .debugOutline()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(DebugMarker.label(title), style = MaterialTheme.typography.bodyLarge, color = TestOrange)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Slim orange bar directly under the title of a screen that exists only in the test app. */
@Composable
fun DebugScreenBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TestOrange)
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .testTag(DebugMarker.BAR_TAG)
    ) {
        Text(
            DebugMarker.BAR_TEXT,
            style = MaterialTheme.typography.labelMedium,
            color = OnTestOrange,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

