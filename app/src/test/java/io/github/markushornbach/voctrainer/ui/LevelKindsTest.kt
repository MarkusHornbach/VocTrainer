package io.github.markushornbach.voctrainer.ui

import androidx.compose.ui.graphics.Color
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.QuestionDirection
import io.github.markushornbach.voctrainer.settings.AppTheme
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.ui.theme.KindColor
import io.github.markushornbach.voctrainer.ui.theme.levelKindColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LevelKindsTest {
    @Test
    fun vocabularyKindsFollowTheDirectionAndTypingConstants() {
        val kinds = InfoTexts.levelRows(QuizMode.VOCABULARY).map { it.kind }
        for (level in 0..Leitner.MAX_LEVEL) {
            val expected = when {
                level >= QuestionDirection.TYPING_FROM_LEVEL -> LevelQuestionKind.DE_TO_EN_TYPE
                level >= QuestionDirection.GERMAN_TO_ENGLISH_FROM_LEVEL -> LevelQuestionKind.DE_TO_EN_CHOOSE
                else -> LevelQuestionKind.EN_TO_DE_CHOOSE
            }
            assertEquals("level $level", expected, kinds[level])
        }
        assertEquals(3, kinds.toSet().size)
        assertEquals(
            listOf(2, 1, 3),
            InfoTexts.levelGroups(InfoTexts.levelRows(QuizMode.VOCABULARY)).map { it.rows.size }
        )
    }

    @Test
    fun verbsHaveOneKindForAllLevels() {
        val groups = InfoTexts.levelGroups(InfoTexts.levelRows(QuizMode.VERBS))
        assertEquals(1, groups.size)
        assertEquals(LevelQuestionKind.VERB_SIMPLE_PAST_CHOOSE, groups.single().kind)
        assertEquals(Leitner.MAX_LEVEL + 1, groups.single().rows.size)
    }

    @Test
    fun verbsWithThreeFormsHaveOneKindNamingBothForms() {
        val groups = InfoTexts.levelGroups(InfoTexts.levelRows(QuizMode.VERBS, threeVerbForms = true))
        assertEquals(LevelQuestionKind.VERB_THREE_FORMS_CHOOSE, groups.single().kind)
        assertEquals("Simple Past + Past Participle · mit Vorschlägen", groups.single().kind.label)
        assertEquals(Leitner.MAX_LEVEL + 1, groups.single().rows.size)
    }

    @Test
    fun everyKindHasATextLabel() {
        assertEquals(
            listOf("EN → DE · mit Vorschlägen", "DE → EN · mit Vorschlägen", "DE → EN · tippen"),
            LevelQuestionKind.entries.take(3).map { it.label }
        )
    }

    private fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun labelTextHasEnoughContrastOnEveryColourInEveryTheme() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val colors = theme.levelKindColors(dark)
            for (kind: KindColor in listOf(colors.enToDeChoose, colors.deToEnChoose, colors.deToEnType)) {
                val ratio = contrast(kind.container, kind.onContainer)
                assertTrue("$theme dark=$dark ratio=$ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun threeColoursDifferInEveryTheme() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val c = theme.levelKindColors(dark)
            assertEquals(3, setOf(c.enToDeChoose.container, c.deToEnChoose.container, c.deToEnType.container).size)
            assertNotEquals(c.enToDeChoose.container, c.deToEnType.container)
        }
    }

    @Test
    fun figureIsOnlyShownForVocabulary() {
        assertTrue(showsFigure(QuizMode.VOCABULARY))
        assertTrue(!showsFigure(QuizMode.VERBS))
    }
}
