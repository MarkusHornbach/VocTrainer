package io.github.markushornbach.voctrainer.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.R

/**
 * A small down arrow at the bottom centre while [visible] (= the content can scroll further);
 * it is gone once the end is reached. Place it last in a Box that holds the scrolling content.
 */
@Composable
fun BoxScope.ScrollHint(visible: Boolean) {
    if (!visible) return
    Icon(
        painterResource(R.drawable.ic_expand_more),
        contentDescription = "Weiter unten geht es weiter",
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp)
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape)
    )
}

/**
 * A vertically scrolling column that shows the [ScrollHint] while there is more below. [modifier]
 * sizes the area (and carries the insets), [contentPadding] is the padding inside the scroll.
 */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    contentPadding: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(state)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
        ScrollHint(state.canScrollForward)
    }
}
