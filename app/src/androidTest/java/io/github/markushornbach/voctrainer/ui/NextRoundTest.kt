package io.github.markushornbach.voctrainer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.data.TEST_BOOK_ID
import io.github.markushornbach.voctrainer.data.VocTrainerDatabase
import io.github.markushornbach.voctrainer.data.closeAfterRunningQueries
import io.github.markushornbach.voctrainer.data.inTestBook
import io.github.markushornbach.voctrainer.data.testDatabase
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.learning.AppClock
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.markAllTipsDone
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * The "Nächste Runde" line and the previewed round in the real app (in-memory database, own
 * settings): the line describes the next round and "Los geht's" starts exactly that round.
 */
@RunWith(AndroidJUnit4::class)
class NextRoundTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: VocTrainerDatabase
    private lateinit var settings: AppSettings

    private companion object {
        const val SETTINGS = "test_next_round_settings"
        const val DEVICE = "test_next_round_device"
        const val TIMEOUT = 5_000L
    }

    private fun reviewed(daysAgo: Long) = AppClock.today().minusDays(daysAgo).atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // 6 words on level 3 (last asked 5 days ago: they can become "gelernt") and 8 on level 1 (due).
    private val words = (1..6).map {
        Vocabulary(english = "high$it", germanMeanings = listOf("hoch$it"), unit = 1, part = "A", coreWord = "high$it")
            .withLevel(3).copy(lastReviewedAt = reviewed(5))
    } + (1..8).map {
        Vocabulary(english = "low$it", germanMeanings = listOf("tief$it"), unit = 1, part = "A", coreWord = "low$it")
            .withLevel(1).copy(lastReviewedAt = reviewed(3))
    }

    @Before
    fun setUp() {
        context.deleteSharedPreferences(SETTINGS)
        context.deleteSharedPreferences(DEVICE)
        db = testDatabase(context)
        settings = AppSettings(context, SETTINGS, DEVICE).apply {
            setAutoSpeak(false)
            markAllTipsDone()
        }
        VocTrainerDatabase.replaceInstanceForTest(db)
        AppSettings.replaceInstanceForTest(settings)
        runBlocking {
            db.vocabularyDao().insertAll(words.inTestBook())
            val now = System.currentTimeMillis()
            db.figureDao().upsert(
                Figure(slot = 0, kind = FigureKind.entries.first().key, pointsAtStart = 0, startedAt = now, updatedAt = now, bookId = TEST_BOOK_ID)
            )
        }
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

    private val viewModel: QuizViewModel
        get() = ViewModelProvider(compose.activity)[QuizViewModel::class.java]

    @Test
    fun theLineDescribesTheNextRoundAndTheStartUsesExactlyThoseWords() {
        compose.setContent { VocTrainerTheme { VocTrainerApp() } }
        compose.waitUntil(TIMEOUT) { viewModel.startState.value.nextRoundLine != null }

        val line = "Nächste Runde: 3 Wörter kannst du fertig lernen."
        assertEquals(line, viewModel.startState.value.nextRoundLine)
        compose.onNodeWithText(line).assertExists()

        val preview = viewModel.nextRound.value!!
        val previewed = preview.pick.words
        assertEquals(10, previewed.size)
        // The three places for words that can become "gelernt" come first; the rest are level 1.
        assertEquals(listOf(3, 3, 3), previewed.take(3).map { it.leitnerLevel })
        assertEquals(7, previewed.drop(3).count { it.leitnerLevel == 1 })

        compose.onNodeWithText("Los geht's").performClick()
        compose.waitUntil(TIMEOUT) { viewModel.round.value is QuizRoundState.Asking }

        val asking = viewModel.round.value as QuizRoundState.Asking
        assertEquals(1, asking.question.number)
        assertEquals(10, asking.totalWords)
        assertEquals(previewed.first().id, asking.question.question.word.id)

        // The preview is made again for the following round.
        compose.waitUntil(TIMEOUT) { viewModel.nextRound.value.let { it != null && it !== preview } }
        assertNotNull(viewModel.nextRound.value)
        assertNotSame(preview, viewModel.nextRound.value)
    }
}
