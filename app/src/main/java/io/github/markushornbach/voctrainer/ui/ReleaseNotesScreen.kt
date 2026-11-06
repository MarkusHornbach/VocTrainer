package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.settings.AppSettings
import java.io.IOException

/** The notes bundled at build time; without a readable file the page shows its fallback text. */
fun loadReleaseNotes(context: Context): List<ReleaseNotesSection> = try {
    context.assets.open("RELEASE_NOTES.md").bufferedReader(Charsets.UTF_8).use { ReleaseNotesParser.parse(it.readText()) }
} catch (e: IOException) {
    emptyList()
}

/**
 * Opens the page once after an update. [idle]: the start screen is on its own, without a round, the
 * selection screen or a tip; only then [onOpen] may be called. Decided again whenever one of these
 * changes; the stored version keeps it from coming twice.
 */
@Composable
fun ReleaseNotesAfterUpdate(settings: AppSettings, sections: List<ReleaseNotesSection>, idle: Boolean, onOpen: () -> Unit) {
    if (!settings.releaseNotesAutoOpen) return
    val lastShown by settings.lastReleaseNotesVersion.collectAsState()
    val firstStartDone by settings.firstStartDone.collectAsState()
    val simulated by settings.releaseNotesUpdateSimulated.collectAsState()
    LaunchedEffect(lastShown, firstStartDone, simulated, idle) {
        val running = BuildConfig.VERSION_NAME
        val hasSection = simulated || sections.any { it.version == running }
        when (ReleaseNotesRules.decide(lastShown, running, firstStartDone, hasSection)) {
            ReleaseNotesAction.NOTHING -> Unit
            ReleaseNotesAction.STORE -> settings.setLastReleaseNotesVersion(running)
            ReleaseNotesAction.SHOW -> if (idle) {
                settings.setLastReleaseNotesVersion(running)
                settings.endReleaseNotesUpdateSimulation()
                onOpen()
            }
        }
    }
}

/** Page "Versionshinweise": all versions of [sections], newest first. */
@Composable
fun ReleaseNotesScreen(sections: List<ReleaseNotesSection>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = { BackTopBar(title = ReleaseNotesTexts.TITLE, onBack = onBack) }) { innerPadding ->
        ScrollColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = 16.dp,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (sections.isEmpty()) Text(ReleaseNotesTexts.EMPTY, style = MaterialTheme.typography.bodyLarge)
            sections.forEach { ReleaseNotesSectionBlock(it) }
        }
    }
}

@Composable
private fun ReleaseNotesSectionBlock(section: ReleaseNotesSection) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column {
            Text(
                ReleaseNotesTexts.versionTitle(section.version),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            section.date?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        section.bullets.forEach { bullet ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics { })
                Text(bullet, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
