package io.github.markushornbach.voctrainer.ui

import android.util.DisplayMetrics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.ui.theme.TestOrange
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.markushornbach.voctrainer.BuildConfig
import androidx.compose.ui.text.font.FontWeight
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import kotlin.math.roundToInt

/**
 * "Alle Info-Seiten": a list of I1–I12; each opens with the current data, and "OK" goes back to
 * the list, so all pages can be checked quickly in every theme.
 */
@Composable
private fun AllInfoPages(quizViewModel: QuizViewModel) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings.getInstance(context) }
    val data = rememberInfoPageData(quizViewModel, settings)
    var listOpen by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf<InfoPage?>(null) }
    OutlinedButton(onClick = { listOpen = true }) { Text(TestToolsTexts.ALL_INFO_PAGES) }
    page?.let {
        InfoPageDialog(it, data) {
            page = null
            listOpen = true
        }
    }
    if (listOpen) {
        AlertDialog(
            onDismissRequest = { listOpen = false },
            title = { Text(TestToolsTexts.ALL_INFO_PAGES) },
            text = {
                ScrollColumn {
                    InfoPage.entries.sortedBy { it.number }.forEach { entry ->
                        Text(
                            "I${entry.number} ${entry.title}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    listOpen = false
                                    page = entry
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { listOpen = false }) { Text("Schließen") } }
        )
    }
}

/** Page "Testwerkzeuge" in the settings; only reachable in the test app (debug build). */
@Composable
fun TestToolsScreen(
    onBack: () -> Unit,
    /** "Tipp sofort zeigen": back to the start screen (or the demo round / result), where the tip waits for its target. */
    onShowTip: () -> Unit,
    viewModel: TestToolsViewModel = viewModel(),
    quizViewModel: QuizViewModel = viewModel()
) {
    BackHandler(onBack = onBack)
    if (!BuildConfig.DEBUG) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        viewModel.clearMessage()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
    }
    var openNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val open = TestToolGroups.decode(openNames)
    fun setOpen(groups: Set<TestToolGroup>) {
        openNames = TestToolGroups.encode(groups)
    }

    Scaffold(
        topBar = { BackTopBar(title = "Testwerkzeuge", onBack = onBack, debugOnly = true) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            DateBar(state, viewModel)
            ScrollColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = 16.dp,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Nur in der Test-App. Die Änderungen wirken sofort auf Startseite, Avatar und Lernstand.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DisplayInfo()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { setOpen(TestToolGroups.all) }, modifier = Modifier.testTag("testGroupsExpandAll")) {
                        Text("Alle aufklappen")
                    }
                    OutlinedButton(onClick = { setOpen(TestToolGroups.none) }, modifier = Modifier.testTag("testGroupsCollapseAll")) {
                        Text("Alle zuklappen")
                    }
                }
                TestToolGroup.entries.forEach { group ->
                    ExpandableGroup(
                        group = group,
                        expanded = group in open,
                        onToggle = { setOpen(TestToolGroups.toggle(open, group)) }
                    ) {
                        when (group) {
                            TestToolGroup.LEVEL -> LevelGroup(state, viewModel, quizViewModel)
                            TestToolGroup.AVATAR -> AvatarGroup(state, viewModel)
                            TestToolGroup.HELP -> HelpGroup(viewModel, quizViewModel, onShowTip)
                            TestToolGroup.BOOKS -> BooksGroup(viewModel)
                            TestToolGroup.CHECK -> {
                                val check by viewModel.check.collectAsState()
                                val candidates by viewModel.checkCandidates.collectAsState()
                                CheckTool(
                                    check = check,
                                    candidates = candidates,
                                    onSearch = viewModel::setCheckSearch,
                                    onPick = viewModel::pickCheckWord,
                                    onInput = viewModel::setCheckInput,
                                    onCheck = viewModel::runCheck
                                )
                            }
                            TestToolGroup.RESET -> ResetGroup(state, viewModel)
                        }
                    }
                }
            }
        }
    }
}

/** Always visible under the bar: the simulated date and the buttons to move it. */
@Composable
private fun DateBar(state: TestToolsState, viewModel: TestToolsViewModel) {
    var help by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("testDateBar"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                TestToolsTexts.simulatedToday(state.dateShiftDays, AppClock.today()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (state.dateShiftDays != 0) FontWeight.Bold else null,
                modifier = Modifier.weight(1f).testTag("testDateText")
            )
            HelpButton(help, tag = "testDateHelp") { help = !help }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 7, 14).forEach { days ->
                OutlinedButton(
                    onClick = { viewModel.shiftDate(days) },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("+$days") }
            }
            OutlinedButton(
                onClick = viewModel::resetDate,
                enabled = state.dateShiftDays != 0,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("Echtes Datum") }
        }
        if (help) HelpText(
            "Wirkt auf Tageswechsel-Regeln, Fälligkeit, Tagesziel, Serie und Erinnerung. Antworten, Punkte und " +
                "Übungstage werden mit dem simulierten Datum gespeichert. Startseite zeigt „Test: Datum“."
        )
    }
}

/** Header row with chevron (whole row clickable) and, when open, the [content] below it. */
@Composable
private fun ExpandableGroup(
    group: TestToolGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val warning = group == TestToolGroup.RESET
    val color = if (warning) MaterialTheme.colorScheme.error else TestOrange
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, color), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .testTag(group.tag)
                .semantics { stateDescription = if (expanded) "aufgeklappt" else "zugeklappt" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (warning) DebugMarker.label(group.title) else group.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (warning) color else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null,
                tint = color
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

/** Test tag of the line under "Anzeige". */
const val TEST_DISPLAY_INFO_TAG = "testDisplayInfo"

/**
 * "Anzeige": font scale and density of the system (application context) and of the app (this screen), so
 * a phone can be checked for the system defaults; the app always uses the defaults (see [FixedUiScale]).
 */
@Composable
private fun DisplayInfo() {
    val system = LocalContext.current.applicationContext.resources.configuration
    val density = LocalDensity.current
    val line = FixedUiScale.testToolsLine(
        systemFontScale = system.fontScale,
        systemDpi = system.densityDpi,
        defaultDpi = DisplayMetrics.DENSITY_DEVICE_STABLE,
        appFontScale = density.fontScale,
        appDpi = (density.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Anzeige", style = MaterialTheme.typography.titleSmall)
        Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(TEST_DISPLAY_INFO_TAG))
    }
}

/** A sub-section with its own header; closed at first. */
@Composable
private fun SubSection(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .testTag(tag)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null
            )
        }
        if (expanded) content()
    }
}

/** The small "?" that shows or hides an explanation. */
@Composable
private fun HelpButton(shown: Boolean, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp).testTag(tag)) {
        Icon(
            painterResource(R.drawable.ic_help),
            contentDescription = if (shown) "Erklärung ausblenden" else "Erklärung anzeigen",
            modifier = Modifier.size(20.dp),
            tint = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** [content] (a button or a switch row) with a "?" next to it that toggles [help]. */
@Composable
private fun WithHelp(help: String, tag: String, content: @Composable RowScope.() -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
            HelpButton(shown, tag) { shown = !shown }
        }
        if (shown) HelpText(help)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelGroup(state: TestToolsState, viewModel: TestToolsViewModel, quizViewModel: QuizViewModel) {
    val pick by viewModel.levelPick.collectAsState()
    val mode = pick.mode(state.mode)
    var helpShown by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lernlevel der Auswahl setzen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            HelpButton(helpShown, tag = "testLevelHelp") { helpShown = !helpShown }
        }
        if (helpShown) {
            HelpText(
                if (mode == QuizMode.VOCABULARY) {
                    "Gilt für die gespeicherte Auswahl im Modus „Vokabeln“ (auch Level erhöhen). Die Wörter sind danach " +
                        "fällig, ihr höchstes Level wird auf dasselbe Level gesetzt (Punkte erneut testbar)."
                } else {
                    "Gilt für die Auswahl im Modus „Verben“."
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(QuizMode.VERBS to "Verben", QuizMode.VOCABULARY to "Vokabeln").forEach { (option, label) ->
                FilterChip(
                    selected = mode == option,
                    onClick = { viewModel.setLevelMode(option) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("levelMode_${option.name}")
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..Leitner.MAX_LEVEL).forEach { level ->
                FilterChip(
                    selected = pick.isSelected(mode, level),
                    onClick = {
                        if (mode == QuizMode.VERBS) viewModel.setVerbLevel(level) else viewModel.setVocabularyLevel(level)
                    },
                    label = { Text("$level") },
                    modifier = Modifier.testTag("level_$level")
                )
            }
        }
    }
    val dueHelp = "Gilt für die Auswahl im aktuellen Modus (" +
        (if (state.mode == QuizMode.VERBS) "Verben" else "Vokabeln") + "); die Level bleiben."
    WithHelp(dueHelp, tag = "testDueHelp") {
        OutlinedButton(onClick = viewModel::makeSelectionDue) { Text("Auswahl jetzt fällig machen") }
    }
    var showNextRound by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { showNextRound = true }) { Text("Nächste Runde anzeigen") }
    if (showNextRound) {
        val preview by quizViewModel.nextRound.collectAsState()
        NextRoundDialog(preview, onDismiss = { showNextRound = false })
    }
}

@Composable
private fun AvatarGroup(state: TestToolsState, viewModel: TestToolsViewModel) {
    Text(
        if (state.stage == null) "Kein Avatar ausgewählt" else "Aktuelle Stufe: ${state.stage}",
        style = MaterialTheme.typography.bodyMedium
    )
    ChipGroup(title = "Stufe setzen") {
        (0..FigureRules.MAX_STAGE).forEach { stage ->
            FilterChip(
                selected = stage == state.stage,
                onClick = { viewModel.setStage(stage) },
                enabled = state.stage != null,
                label = { Text(stage.toString()) }
            )
        }
    }
    PointsField(total = state.totalPoints, loaded = state.loaded, onSet = viewModel::setPoints)
    OutlinedButton(onClick = viewModel::finishFigure, enabled = state.stage != null) { Text("Avatar fertigstellen") }

    val modelLines by viewModel.avatarModelLines.collectAsState()
    SubSection("Avatar-Modell", tag = "testSub_avatarModel") {
        modelLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }

    val badgeLines by viewModel.badgeLines.collectAsState()
    val (earned, possible) = viewModel.badgeCount.collectAsState().value
    SubSection("Abzeichen ($earned von $possible)", tag = "testSub_badges") {
        badgeLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    OutlinedButton(onClick = viewModel::checkBadgesNow) { Text("Abzeichen jetzt prüfen") }
}

@Composable
private fun HelpGroup(viewModel: TestToolsViewModel, quizViewModel: QuizViewModel, onShowTip: () -> Unit) {
    TipTools(
        onShowNow = { tip ->
            quizViewModel.showTipDemo(tip)
            onShowTip()
        }
    )
    val onePerVisit by viewModel.tipsOnePerVisit.collectAsState()
    WithHelp("Aus: Auf der Startseite können alle anstehenden Tipps nacheinander kommen.", tag = "testHelp_onePerVisit") {
        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(value = onePerVisit, role = Role.Switch, onValueChange = viewModel::setTipsOnePerVisit),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ein Tipp pro Besuch", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = onePerVisit, onCheckedChange = null)
        }
    }
    WithHelp(
        "Willkommen, Buchwahl und Avatar-Wahl noch einmal, ohne Daten zu löschen. Hat das Buch " +
            "schon einen Avatar, wird die Wahl nicht gespeichert. Die Tipps bleiben, außer bei „Ohne Tipps starten“.",
        tag = "testHelp_firstStart"
    ) { OutlinedButton(onClick = viewModel::simulateFirstStart) { Text("Erster Start simulieren") } }
    AllInfoPages(quizViewModel)
    val markCorrect by viewModel.markCorrectAnswer.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = markCorrect, role = Role.Switch, onValueChange = viewModel::setMarkCorrectAnswer),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Richtige Antwort markieren", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = markCorrect, onCheckedChange = null)
    }
}

@Composable
private fun BooksGroup(viewModel: TestToolsViewModel) {
    WithHelp(
        "Legt das inaktive Buch „Testbuch“ mit 10 Vokabeln und 2 unregelmäßigen Verben an, um Bücherwechsel, " +
            "Import in ein inaktives Buch und Sicherungen mit zwei Büchern auszuprobieren.",
        tag = "testHelp_testBook"
    ) { OutlinedButton(onClick = viewModel::createTestBook) { Text(TestToolsTexts.CREATE_TEST_BOOK) } }
}

@Composable
private fun ResetGroup(state: TestToolsState, viewModel: TestToolsViewModel) {
    val error = MaterialTheme.colorScheme.error
    val colors = ButtonDefaults.outlinedButtonColors(contentColor = error)
    val border = BorderStroke(1.dp, error)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    WithHelp(
        "Löscht alle Vokabeln und unregelmäßigen Verben des aktiven Buchs endgültig (das Buch, Punkte, " +
            "Avatare und Abzeichen bleiben), damit ein frischer Import und ein erneuter Import ausprobiert werden können.",
        tag = "testHelp_deleteAll"
    ) {
        OutlinedButton(onClick = { confirmDelete = true }, colors = colors, border = border) { Text("Alle Vokabeln entfernen") }
    }
    WithHelp(
        "„Zurücksetzen“ löscht alle Abzeichen; die nächste Prüfung (z. B. Startseite) vergibt alles " +
            "rückwirkend, ohne Hinweis.",
        tag = "testHelp_resetBadges"
    ) {
        OutlinedButton(onClick = viewModel::resetBadges, colors = colors, border = border) { Text("Abzeichen zurücksetzen") }
    }
    OutlinedButton(onClick = viewModel::resetFigures, enabled = state.figureCount > 0, colors = colors, border = border) {
        Text("Avatar zurücksetzen")
    }
    OutlinedButton(onClick = viewModel::resetTips, colors = colors, border = border) { Text("Alle Tipps zurücksetzen") }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Alle Vokabeln entfernen?") },
            text = { Text("Alle Vokabeln und Verben des aktiven Buchs samt Lernstand werden endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAllVocabulary() }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}

/**
 * "Prüfung testen": pick an entry, type an answer and see what the quiz's checker makes of it
 * (with the learning material of all vocabulary and verbs). Changes nothing.
 */
@Composable
private fun CheckTool(
    check: CheckState,
    candidates: List<CheckCandidate>,
    onSearch: (String) -> Unit,
    onPick: (CheckCandidate) -> Unit,
    onInput: (String) -> Unit,
    onCheck: () -> Unit
) {
    OutlinedTextField(
        value = check.search,
        onValueChange = onSearch,
        label = { Text("Eintrag suchen (Englisch oder Deutsch)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    candidates.forEach { candidate ->
        Text(
            candidate.english,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (candidate.id == check.wordId) FontWeight.Bold else null,
            color = if (candidate.id == check.wordId) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(candidate) }
                .padding(vertical = 6.dp)
        )
    }
    Text(
        check.wordEnglish?.let { "Gewählt: $it" } ?: "Noch kein Eintrag gewählt",
        style = MaterialTheme.typography.bodyMedium
    )
    NoSuggestionsTextInput {
        OutlinedTextField(
            value = check.input,
            onValueChange = onInput,
            label = { Text("Eingabe") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onCheck() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
    OutlinedButton(onClick = onCheck, enabled = check.wordId != null) { Text("Prüfen") }
    check.result?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) }
    var helpShown by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Hinweis", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        HelpButton(helpShown, tag = "testHelp_check") { helpShown = !helpShown }
    }
    if (helpShown) {
        HelpText("Nutzt dieselbe Prüfung wie die Abfrage (Lernstoff: alle Vokabeln und Verben). Ändert nichts am Lernstand.")
    }
}

/**
 * "Tipp sofort zeigen": goes to the real screen of the tip with the example book (ignores "done" and
 * the limit "Ein Tipp pro Besuch"); the example book stays the active book afterwards.
 */
@Composable
private fun TipTools(onShowNow: (Tip) -> Unit) {
    SubSection("Tipp sofort zeigen", tag = "testSub_tipShow") {
        HelpText("Wechselt zum Beispielbuch (Modus „Vokabeln“) und zeigt den Tipp an seiner echten Stelle.")
        Tip.entries.forEach { tip ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${tip.id} ${tip.shortName}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onShowNow(tip) }) { Text("Zeigen") }
            }
            HelpText(TestToolsTexts.tipSituation(tip))
        }
    }
}

/** "Punkte setzen": prefilled with the current total once it is known. */
@Composable
private fun PointsField(total: Int, loaded: Boolean, onSet: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (loaded && !prefilled) {
            input = total.toString()
            prefilled = true
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { text -> input = text.filter { it.isDigit() }.take(7) },
            label = { Text("Punkte setzen") },
            supportingText = { Text("Jetzt: ${PracticeTexts.formatNumber(total)}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = { onSet(input) }) { Text("Setzen") }
    }
}

/** Test app: the words of the previewed round, which the next start uses as it is. */
@Composable
private fun NextRoundDialog(preview: NextRoundPreview?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nächste Runde") },
        text = {
            val rows = preview?.reasons.orEmpty()
            if (rows.isEmpty()) {
                Text("Die Auswahl hat keine Wörter für eine Runde.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rows.forEachIndexed { index, (word, reason) ->
                        Text("${index + 1}. ${word.english} · Level ${word.leitnerLevel} · ${reason.label}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}
