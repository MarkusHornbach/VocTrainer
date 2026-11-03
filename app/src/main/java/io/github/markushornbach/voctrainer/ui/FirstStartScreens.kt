package io.github.markushornbach.voctrainer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.ui.theme.TestOrange
import kotlinx.coroutines.launch

/** Test tags of the first start. */
object FirstStartTags {
    const val WELCOME = "first_start_welcome"
    const val BOOK_CHOICE = "first_start_book_choice"
    const val EXAMPLE_HINT = "first_start_example_hint"
}

private enum class FirstStartStep { WELCOME, BOOK, EXAMPLE_HINT, AVATAR }

/**
 * The first start of a fresh installation: welcome → book choice → (only for the example book: a
 * hint where the real book is loaded) → avatar choice; the tips come while the app is used. With
 * "Ohne Tipps starten" every tip is done at the end. Shown instead of every other screen until the
 * learner reaches the start screen ([AppSettings.finishFirstStart]); no tips.
 */
@Composable
fun FirstStartFlow(settings: AppSettings, quizViewModel: QuizViewModel, viewModel: FirstStartViewModel = viewModel()) {
    BlockTips()
    var step by rememberSaveable { mutableStateOf(FirstStartStep.WELCOME) }
    var withTips by rememberSaveable { mutableStateOf(true) }
    var chosenBook by rememberSaveable { mutableStateOf<String?>(null) }
    when (step) {
        FirstStartStep.WELCOME -> WelcomeScreen(
            onStart = {
                withTips = true
                step = FirstStartStep.BOOK
            },
            onWithoutTips = {
                withTips = false
                step = FirstStartStep.BOOK
            }
        )
        FirstStartStep.BOOK -> BookChoiceScreen(
            viewModel = viewModel,
            onBack = { step = FirstStartStep.WELCOME },
            onChosen = { bookId, example ->
                chosenBook = bookId
                step = if (example) FirstStartStep.EXAMPLE_HINT else FirstStartStep.AVATAR
            }
        )
        FirstStartStep.EXAMPLE_HINT -> ExampleBookHint(
            onBack = { step = FirstStartStep.BOOK },
            onOk = { step = FirstStartStep.AVATAR }
        )
        FirstStartStep.AVATAR -> AvatarStep(settings, quizViewModel, chosenBook) {
            if (!withTips) settings.startWithoutTips()
            settings.finishFirstStart()
        }
    }
}

/**
 * The avatar choice as on the start screen; skipped if the book has an avatar already. In the
 * simulation of the test app it is shown anyway, without storing the choice.
 */
@Composable
private fun AvatarStep(settings: AppSettings, quizViewModel: QuizViewModel, bookId: String?, onFinish: () -> Unit) {
    val figure by quizViewModel.figureState.collectAsState()
    val bookTitle by quizViewModel.activeBookTitle.collectAsState()
    val simulated by settings.firstStartSimulated.collectAsState()
    // Decided once, when the state of the chosen book is there.
    var testChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val ready = figure.loaded && figure.bookId == bookId
    LaunchedEffect(ready) {
        if (ready && testChoice == null) testChoice = simulated && figure.current != null
    }
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = { AppTopBar(bookTitle = bookTitle) }) { innerPadding ->
        val modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(16.dp)
        when {
            testChoice == true ->
                FigureChoice(FigureTexts.choiceTitle(figure.choosingNext), onChoose = { onFinish() }, modifier, FirstStartTexts.SIMULATED_CHOICE)
            testChoice == null -> Unit
            figure.needsChoice -> FigureChoice(FigureTexts.choiceTitle(figure.choosingNext), quizViewModel::chooseFigure, modifier)
            else -> LaunchedEffect(Unit) { onFinish() }
        }
    }
}

/** Centered with [content]; scrolls only if it does not fit (large font). */
@Composable
private fun CenteredScrollColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .heightIn(min = this@BoxWithConstraints.maxHeight)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
            ScrollHint(scroll.canScrollForward)
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onWithoutTips: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        CenteredScrollColumn(modifier = Modifier.padding(innerPadding).fillMaxSize().testTag(FirstStartTags.WELCOME)) {
            Image(
                painterResource(FigureImages.forStage(FigureKind.DRAGON, FigureImages.CHOICE_STAGE)),
                contentDescription = null,
                modifier = Modifier.size(160.dp)
            )
            Text(
                FirstStartTexts.welcomeTitle(AppLook.testMarkers).let { if (AppLook.testMarkers) DebugMarker.label(it) else it },
                style = MaterialTheme.typography.headlineSmall,
                color = if (AppLook.testMarkers) TestOrange else Color.Unspecified,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WelcomeLine(R.drawable.ic_book, FirstStartTexts.WELCOME_LEARN)
                WelcomeLine(R.drawable.ic_star, FirstStartTexts.WELCOME_AVATAR)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(FirstStartTexts.START, style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = onWithoutTips) { Text(FirstStartTexts.WITHOUT_TIPS) }
        }
    }
}

@Composable
private fun WelcomeLine(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * "Mit welchem Buch lernst du?": first and large "Mein Buch laden" (the normal import; the book of
 * the file becomes active without asking), below it the example book as a small text link for
 * those without a vocabulary file.
 */
@Composable
private fun BookChoiceScreen(viewModel: FirstStartViewModel, onBack: () -> Unit, onChosen: (bookId: String, example: Boolean) -> Unit) {
    BackHandler(onBack = onBack)
    val result by viewModel.result.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(result) {
        when (val current = result) {
            is BookChoiceResult.Chosen -> {
                viewModel.consumeResult()
                onChosen(current.bookId, current.example)
            }
            BookChoiceResult.Invalid -> {
                viewModel.consumeResult()
                // Not in this effect: consuming the result restarts it.
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(FirstStartTexts.FILE_INVALID)
                }
            }
            null -> Unit
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::loadBook)
    }
    Scaffold(modifier = Modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        CenteredScrollColumn(modifier = Modifier.padding(innerPadding).fillMaxSize().testTag(FirstStartTags.BOOK_CHOICE)) {
            Icon(
                painterResource(R.drawable.ic_book),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                FirstStartTexts.BOOK_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { picker.launch(JSON_MIME_TYPES) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            ) {
                Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(FirstStartTexts.OWN_BOOK, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = viewModel::chooseExampleBook, enabled = !busy) {
                Text(FirstStartTexts.EXAMPLE_BOOK_LINK, textAlign = TextAlign.Center)
            }
        }
    }
}

/** After the example book: where the real book is loaded later; "OK" goes on to the avatar choice. */
@Composable
private fun ExampleBookHint(onBack: () -> Unit, onOk: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        CenteredScrollColumn(modifier = Modifier.padding(innerPadding).fillMaxSize().testTag(FirstStartTags.EXAMPLE_HINT)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(R.drawable.ic_book),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(FirstStartTexts.EXAMPLE_HINT, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Button(onClick = onOk, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(FirstStartTexts.EXAMPLE_HINT_OK, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
