package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertCountEquals
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
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.data.VocabularyRepository
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inMemoryDatabase
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every avatar needs its own words: pause before a new avatar, choice after an import, offset stage table. */
@RunWith(AndroidJUnit4::class)
class AvatarPausedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val SETTINGS = "test_avatar_paused_settings"
        const val DEVICE = "test_avatar_paused_device"
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

    private fun startFirstAvatarAndFinishIt(): QuizViewModel {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        val quiz = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(FigureTexts.choiceTitle(false))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }
        runBlocking { repository.setFigureStageForTest(10) }
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.collection.size == 1 }
        return quiz
    }

    private fun addWords(count: Int) = runBlocking {
        repeat(count) {
            repository.addVocabulary(
                Vocabulary(id = "extra$it", english = "extra$it", germanMeanings = listOf("extra$it"), unit = 9, coreWord = "extra$it")
            )
        }
    }

    @Test
    fun aSmallBookPausesTheNextAvatarAndTheChoiceReturnsAfterAnImport() {
        val quiz = startFirstAvatarAndFinishIt()
        // The example book is far below 563 entries: no choice, the finished avatar stays on the start screen.
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.paused }
        compose.onAllNodes(hasText(FigureTexts.choiceTitle(true))).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Mein Avatar")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(FigureTexts.PAUSED)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(FigureTexts.PAUSED).assertExists()

        // New words arrive: the pause ends.
        val askable = quiz.figureState.value.reach.askable
        addWords(563 - askable)
        compose.waitUntil(TIMEOUT) { !quiz.figureState.value.paused && quiz.figureState.value.needsChoice }
    }

    @Test
    fun theStageTableOfTheSecondAvatarShowsTheOffset() {
        val quiz = startFirstAvatarAndFinishIt()
        addWords(900 - quiz.figureState.value.reach.askable)
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.needsChoice }
        quiz.chooseFigure(FigureKind.MONSTER)
        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }
        compose.onAllNodes(hasContentDescription("Mein Avatar")).onFirst().performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasContentDescription("Erklärung anzeigen")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription("Erklärung anzeigen"))[0].performClick()
        // askable 900: cap 720, base 300, stage 3 needs 310 and stage 4 needs 325 learned words.
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("310")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("325").assertExists()
    }
}
