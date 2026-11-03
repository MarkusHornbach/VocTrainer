package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The compulsory avatar choice on the start screen: with the example book of a fresh install, and a confirmed choice stays. */
@RunWith(AndroidJUnit4::class)
class FigureChoiceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var repository: VocabularyRepository

    private companion object {
        const val SETTINGS = "test_figure_choice_settings"
        const val DEVICE = "test_figure_choice_device"
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
    fun theChoiceWaitsForABookAndAConfirmedChoiceStays() {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        val quiz = ViewModelProvider(compose.activity)[QuizViewModel::class.java]
        val title = FigureTexts.choiceTitle(false)

        // A fresh install has no book until the example book is added at the start; then the choice appears.
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ExampleBook.ID, runBlocking { repository.activeBookNow()?.id })

        compose.onNodeWithText(FigureKind.DRAGON.displayName).performClick()
        compose.onNodeWithText("Ja").performClick()

        compose.waitUntil(TIMEOUT) { quiz.figureState.value.current != null }
        compose.waitForIdle()
        compose.onNodeWithText(title).assertDoesNotExist()
        assertEquals(FigureKind.DRAGON, quiz.figureState.value.kind)
    }
}
