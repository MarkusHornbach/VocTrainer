package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.ExampleBook
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inMemoryDatabase
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import io.github.markushornbach.voctrainer.learning.UnitChoice
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Mein Avatar" shows the learned words of the whole book, not of the quiz selection. */
@RunWith(AndroidJUnit4::class)
class FigureLearnedWordsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val SETTINGS = "test_figure_learned_settings"
        const val DEVICE = "test_figure_learned_device"
        const val TIMEOUT = 5_000L
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = inMemoryDatabase(context)
        repository = VocabularyRepository(db)
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(
            AppSettings(context, SETTINGS, DEVICE).apply {
                setAutoSpeak(false)
                markAllTipsDone()
            }
        )
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        VocTrainerDatabase.replaceInstanceForTest(null)
        AppSettings.replaceInstanceForTest(null)
        db.closeAfterRunningQueries()
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
    }

    @Test
    fun theLineShowsTheLearnedWordsOfTheWholeBookNotOfTheSelection() {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        val quiz = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(FigureTexts.choiceTitle(false))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }

        // 5 words from several units are learned; the quiz selection is one unit that covers only some of them.
        val words = runBlocking { repository.vocabularyInInsertionOrder.first() }.filter { !it.isDeleted }
        val learned = words.filter { it.unit == 1 }.take(3) + words.filter { it.unit == 2 }.take(2)
        runBlocking { repository.setVocabularyLevels(learned.map { it.id }, 4) }
        quiz.selectUnit(UnitChoice.Number(1))
        quiz.applySelection()

        compose.waitUntil(TIMEOUT) { quiz.figureState.value.learnedWords == 5 }
        compose.onAllNodes(hasContentDescription("Mein Avatar")).onFirst().performClick()

        val line = FigureTexts.pointsAndLearned(quiz.figureState.value.totalPoints, 5)
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(line)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(line).assertExists()

        // The first ⓘ belongs to the stage line, the second to the points line.
        compose.onAllNodes(hasContentDescription("Erklärung anzeigen"))[1].performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(InfoTexts.POINTS_TITLE)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theFinishedAvatarStaysOnMeinAvatarWhileTheNextOneIsPaused() {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        val quiz = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(FigureTexts.choiceTitle(false))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }

        // The example book has fewer than 150 free words: a finished avatar pauses the next one.
        val figure = quiz.figureState.value.current!!
        runBlocking {
            db.figureDao().upsert(figure.copy(stage = 10, finishedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        }
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.paused }
        compose.onAllNodes(hasContentDescription("Mein Avatar")).onFirst().performClick()

        val stageLine = FigureTexts.stageLine(FigureKind.DRAGON, 10)
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(stageLine)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(FigureTexts.FINISHED).assertExists()
        compose.onNodeWithText(FigureTexts.PAUSED).assertExists()
        // The big image of the finished avatar (the collection shows a small one as well).
        compose.onAllNodes(hasContentDescription(FigureKind.DRAGON.displayName)).fetchSemanticsNodes().isNotEmpty()
            .let { org.junit.Assert.assertTrue(it) }
    }
}
