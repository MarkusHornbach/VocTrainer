package io.github.markushornbach.voctrainer.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDate
import java.time.ZoneId
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.reminder.Reminder
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.AppTheme
import io.github.markushornbach.voctrainer.settings.VerbFormMode
import io.github.markushornbach.voctrainer.speech.Speaker
import io.github.markushornbach.voctrainer.speech.SpeechState
import io.github.markushornbach.voctrainer.ui.theme.TestOrange
import io.github.markushornbach.voctrainer.ui.theme.colorScheme

/** Main settings menu; each entry opens its own page. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenManageVocabulary: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenLearning: () -> Unit,
    onOpenTestTools: () -> Unit
) {
    val theme by settings.theme.collectAsState()
    val vibration by settings.vibration.collectAsState()
    val autoSpeak by settings.autoSpeak.collectAsState()
    val goal by settings.dailyGoal.collectAsState()
    val reminder by settings.reminderEnabled.collectAsState()
    val minutes by settings.reminderMinutes.collectAsState()
    val verbForms by settings.verbFormMode.collectAsState()

    SettingsPage(title = "Einstellungen", onBack = onBack) {
        MenuCard(
            "Vokabeln verwalten",
            if (BuildConfig.DEBUG) "Importieren, Sicherung, Liste, ${RestartBookTexts.TITLE}"
            else "Importieren, Sicherung, ${RestartBookTexts.TITLE}",
            onOpenManageVocabulary
        )
        MenuCard(
            "Farben, Ton & Vibration",
            theme.label + " · Vorsprechen " + (if (autoSpeak) "an" else "aus") +
                " · Vibration " + if (vibration) "an" else "aus",
            onOpenAppearance
        )
        MenuCard(LearningSettingsTexts.TITLE, "Verbformen: ${verbForms.count} Formen", onOpenLearning)
        MenuCard(
            "Tagesziel & Erinnerung",
            "$goal Runden pro Tag · " +
                if (reminder) "Erinnerung um ${timeLabel(minutes)}" else "Erinnerung aus",
            onOpenPractice
        )
        if (BuildConfig.DEBUG) {
            MenuCard("Testwerkzeuge", "Avatar, Punkte, Lernlevel, Hinweise", onOpenTestTools, debug = true)
        }
        VersionInfo()
    }
}

/**
 * The section "Lernstand zurücksetzen" with the button "Buch neu beginnen" for the active book, at the bottom of "Vokabeln verwalten". Dialog 1
 * says what is reset and what stays, then the backup is saved (same save dialog as "Sicherung
 * exportieren"), then dialog 2 asks a last time. Cancelling at any point resets nothing. The test
 * app can skip the backup. The example book has no backup step: dialog 1 restarts it. The notices
 * go to [snackbarHostState] of the page.
 */
@Composable
fun RestartBookSection(snackbarHostState: SnackbarHostState, viewModel: ManageProgressViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val step by viewModel.step.collectAsState()
    val title = state.bookTitle.orEmpty()
    val error = MaterialTheme.colorScheme.error
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> viewModel.backupChosen(uri) }
    when (step) {
        RestartStep.NONE -> Unit
        RestartStep.ASK -> AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(RestartBookTexts.ASK_TITLE) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(RestartBookTexts.askText(title, state.isExampleBook))
                }
            },
            confirmButton = {
                if (state.isExampleBook) {
                    TextButton(onClick = viewModel::confirmRestart, colors = ButtonDefaults.textButtonColors(contentColor = error)) {
                        Text(RestartBookTexts.CONFIRM)
                    }
                } else {
                    TextButton(onClick = {
                        viewModel.startBackup()
                        backupLauncher.launch(backupFileName(LocalDate.now(), BuildConfig.DEBUG))
                    }) { Text(RestartBookTexts.SAVE_BACKUP) }
                }
            },
            dismissButton = {
                Row {
                    if (BuildConfig.DEBUG && !state.isExampleBook) {
                        TextButton(onClick = viewModel::skipBackupForTest) {
                            Text(DebugMarker.label(RestartBookTexts.WITHOUT_BACKUP), color = TestOrange)
                        }
                    }
                    TextButton(onClick = viewModel::cancel) { Text(RestartBookTexts.CANCEL) }
                }
            }
        )
        RestartStep.CONFIRM, RestartStep.CONFIRM_WITHOUT_BACKUP -> AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(RestartBookTexts.CONFIRM_TITLE) },
            text = {
                Text(
                    if (step == RestartStep.CONFIRM) RestartBookTexts.CONFIRM_TEXT
                    else RestartBookTexts.CONFIRM_TEXT_WITHOUT_BACKUP
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestart, colors = ButtonDefaults.textButtonColors(contentColor = error)) {
                    Text(RestartBookTexts.CONFIRM)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancel) { Text(RestartBookTexts.CANCEL) } }
        )
    }

    val notice by viewModel.notice.collectAsState()
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearNotice()
    }

    var showInfo by rememberSaveable { mutableStateOf(false) }
    if (showInfo) RestartInfoDialog { showInfo = false }
    RestartButtonBlock(
        enabled = state.canRestart,
        onAsk = viewModel::askRestart,
        onInfo = { showInfo = true }
    )
}

/** Test tags of the ⓘ on "Lernstand zurücksetzen" and "Tagesziel & Erinnerung". */
const val RESTART_INFO_TAG = "restart_info"
const val PRACTICE_INFO_TAG = "practice_info"

/**
 * The header "Lernstand zurücksetzen" with its ⓘ and the button "Buch neu beginnen"; while there
 * is nothing to reset the button is disabled and says why. The ⓘ stays usable.
 */
@Composable
private fun RestartButtonBlock(enabled: Boolean, onAsk: () -> Unit, onInfo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(RestartBookTexts.SECTION_TITLE, onInfo, RESTART_INFO_TAG)
        OutlinedButton(onClick = onAsk, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(RestartBookTexts.TITLE)
        }
        if (!enabled) {
            Text(
                RestartBookTexts.NOTHING_TO_RESET,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Small, muted block at the bottom of the settings menu: version and build time. */
@Composable
private fun VersionInfo() {
    val lines = remember {
        VersionText.lines(BuildConfig.VERSION_NAME, BuildConfig.BUILD_TIME, BuildConfig.DEBUG, ZoneId.systemDefault())
    }
    Column(modifier = Modifier.padding(top = 24.dp)) {
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Farben, Ton & Vibration": theme, pronunciation and vibration. */
@Composable
fun AppearanceSettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val selectedTheme by settings.theme.collectAsState()
    val vibration by settings.vibration.collectAsState()

    SettingsPage(title = "Farben, Ton & Vibration", onBack = onBack) {
        Text("Farben", style = MaterialTheme.typography.titleMedium)
        AppTheme.entries.forEach { theme ->
            ThemeCard(
                theme = theme,
                selected = theme == selectedTheme,
                onSelect = { settings.setTheme(theme) }
            )
        }

        PronunciationSection(settings)

        Text(
            "Vibration",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vibration bei falschen Antworten", modifier = Modifier.weight(1f))
            Switch(checked = vibration, onCheckedChange = settings::setVibration)
        }
    }
}

/** "Aussprache": automatic speech, and a way to install a voice if there is no English one. */
@Composable
private fun PronunciationSection(settings: AppSettings) {
    val context = LocalContext.current
    val autoSpeak by settings.autoSpeak.collectAsState()
    val speech by remember(context) { Speaker.getInstance(context).state }.collectAsState()

    Text("Aussprache", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Wörter automatisch vorsprechen", modifier = Modifier.weight(1f))
        Switch(checked = autoSpeak, onCheckedChange = settings::setAutoSpeak)
    }
    Text(
        "Englische Wörter werden vorgesprochen. Fragt die App auf Deutsch, kommt das englische Wort erst nach der Antwort.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (speech == SpeechState.UNAVAILABLE) {
        Text(
            "Auf diesem Gerät ist keine englische Stimme installiert.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onClick = {
            try {
                context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            } catch (e: ActivityNotFoundException) {
                // No engine offers voice downloads; nothing else to do here.
            }
        }) { Text("Stimme installieren") }
    }
}

/** Texts of the page "Üben". */
object LearningSettingsTexts {
    const val TITLE = "Üben"
    const val VERB_FORMS = "Verbformen"
    const val VERB_FORMS_TEXT =
        "Bei unregelmäßigen Verben übst du das Simple Past. Mit 3 Formen kommt danach das Past Participle. " +
            "Deine Level bleiben beim Umstellen gleich."

    /** Test tag of the option for [mode]. */
    fun tag(mode: VerbFormMode): String = "verb_forms_${mode.count}"
}

/** "Üben": how the verb training asks ("Verbformen"). */
@Composable
fun LearningSettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val selected by settings.verbFormMode.collectAsState()
    SettingsPage(title = LearningSettingsTexts.TITLE, onBack = onBack) {
        Text(LearningSettingsTexts.VERB_FORMS, style = MaterialTheme.typography.titleMedium)
        Text(
            LearningSettingsTexts.VERB_FORMS_TEXT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerbFormMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LearningSettingsTexts.tag(mode))
                    .selectable(selected = mode == selected, onClick = { settings.setVerbFormMode(mode) }, role = Role.RadioButton)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mode == selected, onClick = null)
                Text(mode.label, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** "Tagesziel & Erinnerung": daily goal and daily reminder. */
@Composable
fun PracticeSettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    SettingsPage(title = "Tagesziel & Erinnerung", onBack = onBack) {
        DailyGoalSection(settings)
        ReminderSection(settings)
    }
}

/** Scrollable settings page with back arrow; the system back button does the same. */
@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = { BackTopBar(title = title, onBack = onBack) },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } }
    ) { innerPadding ->
        ScrollColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = 16.dp,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit, debug: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (debug) Modifier.debugOutline() else Modifier)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (debug) DebugMarker.label(title) else title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (debug) TestOrange else Color.Unspecified
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

private fun timeLabel(minutes: Int): String = "%02d:%02d Uhr".format(minutes / 60, minutes % 60)

@Composable
private fun DailyGoalSection(settings: AppSettings) {
    val goal by settings.dailyGoal.collectAsState()
    var showInfo by rememberSaveable { mutableStateOf(false) }
    if (showInfo) PracticeSettingsInfoDialog { showInfo = false }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tagesziel", style = MaterialTheme.typography.titleMedium)
        InfoButton(onClick = { showInfo = true }, modifier = Modifier.testTag(PRACTICE_INFO_TAG))
    }
    Text(
        "Runden pro Tag (nur Vokabelrunden zählen, nicht das Verbentraining)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettings.DAILY_GOAL_RANGE.forEach { value ->
            FilterChip(
                selected = value == goal,
                onClick = { settings.setDailyGoal(value) },
                label = { Text(value.toString()) }
            )
        }
    }
}

@Composable
private fun ReminderSection(settings: AppSettings) {
    val context = LocalContext.current
    val enabled by settings.reminderEnabled.collectAsState()
    val minutes by settings.reminderMinutes.collectAsState()
    var allowed by remember { mutableStateOf(Reminder.notificationsAllowed(context)) }
    // The user may have changed the permission in the Android settings in the meantime.
    LifecycleResumeEffect(Unit) {
        allowed = Reminder.notificationsAllowed(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        settings.setReminderEnabled(granted)
        Reminder.schedule(context)
    }

    Text("Erinnerung", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Row(
        modifier = Modifier.toggleable(
            value = enabled,
            role = Role.Switch,
            onValueChange = { on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !allowed) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settings.setReminderEnabled(on)
                    Reminder.schedule(context)
                }
            }
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Tägliche Erinnerung", modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = null)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                TimePickerDialog(context, { _, hour, minute ->
                    settings.setReminderMinutes(hour * 60 + minute)
                    Reminder.schedule(context)
                }, minutes / 60, minutes % 60, true).show()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Uhrzeit", modifier = Modifier.weight(1f))
        Text(
            timeLabel(minutes),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!allowed) {
        Text(
            "Benachrichtigungen sind für die App blockiert. Du kannst sie in den Android-Einstellungen erlauben.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ThemeCard(theme: AppTheme, selected: Boolean, onSelect: () -> Unit) {
    val preview = theme.colorScheme(isSystemInDarkTheme())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preview in the theme's own colours: background with primary and accent.
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(preview.background)
                    .padding(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorDot(preview.primary)
                    ColorDot(preview.tertiary)
                }
            }
            Text(theme.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
    )
}
