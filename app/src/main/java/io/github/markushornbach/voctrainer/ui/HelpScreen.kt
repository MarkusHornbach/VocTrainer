package io.github.markushornbach.voctrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.VerbFormMode
import kotlinx.coroutines.launch

/**
 * What the info pages show of the current state: the current avatar (without one, the stage table
 * of the first avatar of this book), the streak, this device's goal and the verb forms.
 */
@Composable
fun rememberInfoPageData(viewModel: QuizViewModel, settings: AppSettings): InfoPageData {
    val figure by viewModel.figureState.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val goal by settings.dailyGoal.collectAsState()
    val verbForms by settings.verbFormMode.collectAsState()
    return InfoPageData(
        figure.current?.stage ?: 0, figure.reach, streak, goal, threeVerbForms = verbForms == VerbFormMode.THREE
    )
}

/**
 * Page "Hilfe": the info pages of the app in groups and "Tipps wieder zeigen".
 */
@Composable
fun HelpScreen(viewModel: QuizViewModel, settings: AppSettings, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val data = rememberInfoPageData(viewModel, settings)
    var page by rememberSaveable { mutableStateOf<InfoPage?>(null) }
    page?.let { InfoPageDialog(it, data) { page = null } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = { BackTopBar(title = "Hilfe", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        ScrollColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = 16.dp,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            InfoGroup.entries.forEachIndexed { index, group ->
                val entries = InfoPage.entries.filter { it.group == group }
                if (entries.isEmpty()) return@forEachIndexed
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp)
                        .semantics { heading() }
                )
                entries.forEach { entry ->
                    HelpRow(entry.title, R.drawable.ic_info, onClickLabel = "Erklärung öffnen") { page = entry }
                }
            }
            // All tips at once and without asking: showing them again does no harm.
            HelpRow("💡 " + TipTexts.SHOW_AGAIN, icon = null, onClickLabel = TipTexts.SHOW_AGAIN) {
                settings.resetTips()
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(TipTexts.SHOWN_AGAIN)
                }
            }
        }
    }
}

@Composable
private fun HelpRow(title: String, icon: Int?, onClickLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (icon != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
