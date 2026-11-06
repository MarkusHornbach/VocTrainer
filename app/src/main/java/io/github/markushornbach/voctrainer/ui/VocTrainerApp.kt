package io.github.markushornbach.voctrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import io.github.markushornbach.voctrainer.ui.theme.TestOrange
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.Tip

/**
 * Simple state-based navigation: quiz → "Mein Avatar"; quiz → help; quiz → settings → manage vocabulary →
 * edit list → edit page, settings → "Farben, Ton & Vibration" / "Tagesziel & Erinnerung" /
 * "Testwerkzeuge" (test app only).
 */
private enum class AppScreen {
    Quiz, Figure, Help, Settings, AppearanceSettings, PracticeSettings, LearningSettings, ManageVocabulary, VocabularyList, VocabularyEdit,
    ReleaseNotes,
    /** Test app (debug build) only. */
    TestTools
}

/** Test tag of the active book's title on the start screen. */
const val BOOK_TITLE_TAG = "start_book_title"

@Composable
fun VocTrainerApp() {
    // Saveable, so the screen survives an activity recreation while a file picker is open.
    var screen by rememberSaveable { mutableStateOf(AppScreen.Quiz) }
    // The edit page was opened from a quiz question (edit button): closing it returns to the round.
    var editFromQuiz by rememberSaveable { mutableStateOf(false) }
    // "Versionshinweise" opened by itself after an update: closing it returns to the start screen.
    var releaseNotesFromStart by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val releaseNotes = remember { loadReleaseNotes(context) }
    val settings = remember { AppSettings.getInstance(context) }
    val quizViewModel: QuizViewModel = viewModel()
    val editorViewModel: VocabularyEditorViewModel = viewModel()
    // The visit rule lives in the ViewModel, so an activity recreation does not start a new visit.
    val tipController = remember(quizViewModel) { TipController(quizViewModel.startVisit) }

    // Tapping the reminder notification opens the quiz start screen.
    val startScreenRequested by quizViewModel.startScreenRequested.collectAsState()
    LaunchedEffect(startScreenRequested) {
        if (startScreenRequested) {
            screen = AppScreen.Quiz
            quizViewModel.consumeStartScreenRequest()
        }
    }

    val firstStartDone by settings.firstStartDone.collectAsState()
    val round by quizViewModel.round.collectAsState()
    val selectionOpen by quizViewModel.filtersExpanded.collectAsState()
    ReleaseNotesAfterUpdate(
        settings = settings,
        sections = releaseNotes,
        idle = firstStartDone && screen == AppScreen.Quiz && round == null && !selectionOpen && tipController.shown == null,
        onOpen = {
            releaseNotesFromStart = true
            quizViewModel.startVisit.end()
            screen = AppScreen.ReleaseNotes
        }
    )

    CompositionLocalProvider(
        LocalTipController provides tipController,
        LocalCompactHeight provides rememberIsCompactHeight()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The first start replaces every screen; the reminder notification does not leave it.
            if (!firstStartDone) {
                LaunchedEffect(Unit) { screen = AppScreen.Quiz }
                FirstStartFlow(settings, quizViewModel)
            } else AppScreens(
                screen = screen,
                onScreen = {
                    // Leaving the start screen ends the visit: the next one may show a tip again.
                    if (it != AppScreen.Quiz) quizViewModel.startVisit.end()
                    screen = it
                },
                editFromQuiz = editFromQuiz,
                onEditFromQuiz = { editFromQuiz = it },
                releaseNotes = releaseNotes,
                releaseNotesFromStart = releaseNotesFromStart,
                onReleaseNotesFromStart = { releaseNotesFromStart = it },
                settings = settings,
                quizViewModel = quizViewModel,
                editorViewModel = editorViewModel
            )
            // Above every screen; no tip has a second button at the moment.
            TipHost(tipController, settings) { }
        }
    }
}

@Composable
private fun AppScreens(
    screen: AppScreen,
    onScreen: (AppScreen) -> Unit,
    editFromQuiz: Boolean,
    onEditFromQuiz: (Boolean) -> Unit,
    releaseNotes: List<ReleaseNotesSection>,
    releaseNotesFromStart: Boolean,
    onReleaseNotesFromStart: (Boolean) -> Unit,
    settings: AppSettings,
    quizViewModel: QuizViewModel,
    editorViewModel: VocabularyEditorViewModel
) {
    when (screen) {
        AppScreen.Quiz -> {
            val round by quizViewModel.round.collectAsState()
            // The selection screen is left only with its own buttons.
            val selectionOpen by quizViewModel.filtersExpanded.collectAsState()
            val simulatedDate by quizViewModel.simulatedDate.collectAsState()
            val bookTitle by quizViewModel.activeBookTitle.collectAsState()
            val tips by settings.tips.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            // Once after "Ohne Tipps starten", on the first start screen; used up even if it times out.
            LaunchedEffect(round == null) {
                if (round != null || !settings.tipsOffHintPending) return@LaunchedEffect
                settings.consumeTipsOffHint()
                snackbarHostState.showSnackbar(
                    FirstStartTexts.TIPS_OFF_HINT,
                    actionLabel = FirstStartTexts.TIPS_OFF_OK,
                    duration = SnackbarDuration.Long
                )
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    // A low window needs the height for the question.
                    if (!LocalCompactHeight.current || round !is QuizRoundState.Asking) AppTopBar(bookTitle = bookTitle, simulatedDate = simulatedDate) {
                        if (round !is QuizRoundState.Asking && !selectionOpen) {
                            // The closing tip "Jetzt kennst du alles Wichtige" points at "?" on the start screen.
                            IconButton(
                                onClick = { onScreen(AppScreen.Help) },
                                modifier = if (round == null) {
                                    Modifier.tipTarget(Tip.ALL_KNOWN, triggered = allKnownTriggered(tips.done))
                                } else {
                                    Modifier
                                }
                            ) {
                                Icon(painterResource(R.drawable.ic_help), contentDescription = "Hilfe")
                            }
                            IconButton(onClick = { onScreen(AppScreen.Settings) }) {
                                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Einstellungen")
                            }
                        }
                    }
                }
            ) { innerPadding ->
                QuizScreen(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = quizViewModel,
                    onOpenFigure = { onScreen(AppScreen.Figure) },
                    onEditWord = { id ->
                        if (BuildConfig.DEBUG) editorViewModel.startEditingById(
                            id,
                            onMissing = quizViewModel::resumeAfterEdit,
                            onReady = {
                                onEditFromQuiz(true)
                                onScreen(AppScreen.VocabularyEdit)
                            }
                        )
                    }
                )
            }
        }
        AppScreen.Figure -> FigureScreen(viewModel = quizViewModel, onBack = { onScreen(AppScreen.Quiz) })
        AppScreen.Help -> HelpScreen(
            viewModel = quizViewModel,
            settings = settings,
            onBack = { onScreen(AppScreen.Quiz) }
        )
        AppScreen.Settings -> SettingsScreen(
            settings = settings,
            onBack = { onScreen(AppScreen.Quiz) },
            onOpenManageVocabulary = { onScreen(AppScreen.ManageVocabulary) },
            onOpenAppearance = { onScreen(AppScreen.AppearanceSettings) },
            onOpenPractice = { onScreen(AppScreen.PracticeSettings) },
            onOpenLearning = { onScreen(AppScreen.LearningSettings) },
            onOpenTestTools = { if (BuildConfig.DEBUG) onScreen(AppScreen.TestTools) },
            onOpenReleaseNotes = {
                onReleaseNotesFromStart(false)
                onScreen(AppScreen.ReleaseNotes)
            }
        )
        AppScreen.ReleaseNotes -> ReleaseNotesScreen(
            sections = releaseNotes,
            onBack = { onScreen(if (releaseNotesFromStart) AppScreen.Quiz else AppScreen.Settings) }
        )
        AppScreen.TestTools -> TestToolsScreen(
            onBack = { onScreen(AppScreen.Settings) },
            onShowTip = { onScreen(AppScreen.Quiz) },
            quizViewModel = quizViewModel
        )
        AppScreen.AppearanceSettings -> AppearanceSettingsScreen(settings, onBack = { onScreen(AppScreen.Settings) })
        AppScreen.PracticeSettings -> PracticeSettingsScreen(settings, onBack = { onScreen(AppScreen.Settings) })
        AppScreen.LearningSettings -> LearningSettingsScreen(settings, onBack = { onScreen(AppScreen.Settings) })
        AppScreen.ManageVocabulary -> ManageVocabularyScreen(
            onBack = { onScreen(AppScreen.Settings) },
            onOpenEditor = {
                if (BuildConfig.DEBUG) {
                    editorViewModel.startWithQuizSelection()
                    onScreen(AppScreen.VocabularyList)
                }
            }
        )
        AppScreen.VocabularyList -> VocabularyListScreen(
            viewModel = editorViewModel,
            onBack = { onScreen(AppScreen.ManageVocabulary) },
            onEdit = { entry ->
                editorViewModel.startEditing(entry)
                onScreen(AppScreen.VocabularyEdit)
            }
        )
        AppScreen.VocabularyEdit -> VocabularyEditScreen(
            viewModel = editorViewModel,
            onClose = {
                if (editFromQuiz) {
                    onEditFromQuiz(false)
                    editorViewModel.clearMessage()
                    onScreen(AppScreen.Quiz)
                    quizViewModel.resumeAfterEdit()
                } else {
                    onScreen(AppScreen.VocabularyList)
                }
            }
        )
    }
}

@Composable
private fun ManageVocabularyScreen(onBack: () -> Unit, onOpenEditor: () -> Unit) {
    BackHandler(onBack = onBack)
    // For the notices of "Buch neu beginnen" at the bottom of the page.
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackTopBar(title = "Vokabeln verwalten", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        VocabularyScreen(onOpenEditor = onOpenEditor, snackbarHostState = snackbarHostState, modifier = Modifier.padding(innerPadding))
    }
}

/**
 * The bar of the start screen: the app name, below it the active book, the simulated date of the
 * test app and [actions] (help and settings).
 */
@Composable
fun AppTopBar(bookTitle: String?, simulatedDate: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 16.dp, end = 4.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The book line sits inside the bar's fixed height and keeps its place while
        // loading (empty without a book), so neither it nor the title ever moves.
        Column(modifier = Modifier.weight(1f)) {
            Text(text = appName(AppLook.testMarkers), style = MaterialTheme.typography.titleLarge)
            Text(
                text = bookTitle.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(BOOK_TITLE_TAG)
            )
        }
        // Test app only: "Datum verschieben" is active.
        simulatedDate?.let {
            Text(
                text = DebugMarker.label(it),
                style = MaterialTheme.typography.labelMedium,
                color = TestOrange,
                modifier = Modifier
                    .debugOutline()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        actions()
    }
}

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, debugOnly: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Zurück")
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        // Screens that exist only in the test app carry the shared marker directly under the title.
        if (debugOnly) DebugScreenBar()
    }
}
