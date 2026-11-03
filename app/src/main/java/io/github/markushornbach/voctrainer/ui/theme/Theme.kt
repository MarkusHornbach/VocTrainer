package io.github.markushornbach.voctrainer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.markushornbach.voctrainer.settings.AppTheme

/**
 * Colours of the quiz answer buttons. Material 3 has no "success" role, so green is defined here;
 * wrong answers use the scheme's error colours. [optionContainer] is the colour of unanswered buttons and
 * is chosen per theme to differ clearly from both green and red. [almostContainer] is the calm colour of
 * "Fast richtig" (neither the error red nor the full green).
 */
data class AnswerColors(
    val correctContainer: Color,
    val onCorrectContainer: Color,
    val optionContainer: Color,
    val onOptionContainer: Color,
    val almostContainer: Color,
    val onAlmostContainer: Color
)

private val GamingAnswerColors = AnswerColors(
    CorrectContainerGaming, OnCorrectContainerGaming, Color(0xFF3C3489), Color(0xFFEEEDFE), Color(0xFFF2C14E), Color(0xFF2A1F00)
)
private val OceanLightAnswerColors = AnswerColors(
    CorrectContainerLight, OnCorrectContainerLight, Color(0xFFD3E4FF), Color(0xFF001C38), Color(0xFFFFE6A8), Color(0xFF3A2A00)
)
private val OceanDarkAnswerColors = AnswerColors(
    CorrectContainerDark, OnCorrectContainerDark, Color(0xFF0F4A82), Color(0xFFD3E4FF), Color(0xFF5C4A12), Color(0xFFFFE6A8)
)
private val SunnyLightAnswerColors = AnswerColors(
    CorrectContainerLight, OnCorrectContainerLight, Color(0xFFFFE3B0), Color(0xFF3A2400), Color(0xFFC4E8EE), Color(0xFF00363D)
)
private val SunnyDarkAnswerColors = AnswerColors(
    CorrectContainerDark, OnCorrectContainerDark, Color(0xFF5F4100), Color(0xFFFFDEA8), Color(0xFF1F4F56), Color(0xFFC4E8EE)
)

val LocalAnswerColors = staticCompositionLocalOf { GamingAnswerColors }

/** Background and text colour of one chip in the level list of "Was bedeuten die Level?". */
data class KindColor(val container: Color, val onContainer: Color)

/**
 * Colours of the three ways a word is asked (see `LevelQuestionKind`): blue, amber and violet – never the
 * green or red of the answers. Used only in the level list of the info box.
 */
data class LevelKindColors(val enToDeChoose: KindColor, val deToEnChoose: KindColor, val deToEnType: KindColor)

private val LightLevelKindColors = LevelKindColors(
    enToDeChoose = KindColor(Color(0xFFCFE2FF), Color(0xFF0A2A52)),
    deToEnChoose = KindColor(Color(0xFFFFE08A), Color(0xFF3A2A00)),
    deToEnType = KindColor(Color(0xFFE3D4FF), Color(0xFF2A0F5C))
)
private val DarkLevelKindColors = LevelKindColors(
    enToDeChoose = KindColor(Color(0xFF1F4F86), Color(0xFFD6E6FF)),
    deToEnChoose = KindColor(Color(0xFF7A5200), Color(0xFFFFE9B5)),
    deToEnType = KindColor(Color(0xFF5B3A99), Color(0xFFEADFFF))
)

// On the deep violet of "Gaming dunkel" dark chips would vanish; brighter ones with dark text are used.
private val GamingLevelKindColors = LevelKindColors(
    enToDeChoose = KindColor(Color(0xFF6FB1F5), Color(0xFF001C38)),
    deToEnChoose = KindColor(Color(0xFFF2B84B), Color(0xFF2C1800)),
    deToEnType = KindColor(Color(0xFFB9A0F0), Color(0xFF1A0B3D))
)

val LocalLevelKindColors = staticCompositionLocalOf { GamingLevelKindColors }

fun AppTheme.levelKindColors(systemDark: Boolean): LevelKindColors = when {
    this == AppTheme.GAMING_DARK -> GamingLevelKindColors
    systemDark -> DarkLevelKindColors
    else -> LightLevelKindColors
}

/**
 * Accent of the figure's stage bar: the scheme's tertiary (orange/gold), except in "Gaming dunkel",
 * whose turquoise tertiary is too close to the green "correct" colour; gold is used there.
 */
private val GamingFigureAccent = Color(0xFFFFC857)

val LocalFigureAccent = staticCompositionLocalOf { GamingFigureAccent }

/**
 * Line of the frame "Ziel" around level 4 and 5 in "Dein Lernstand": the theme's accent (tertiary),
 * darker where it would vanish on the light level chips – "Ozean" light and "Gaming dunkel", whose
 * chips are bright.
 */
private val OceanLightGoalFrame = Color(0xFF9A5A00)
private val GamingGoalFrame = Color(0xFF5C3B00)

val LocalGoalFrameColor = staticCompositionLocalOf { GamingGoalFrame }

private fun AppTheme.goalFrameColor(systemDark: Boolean, scheme: ColorScheme): Color = when {
    this == AppTheme.GAMING_DARK -> GamingGoalFrame
    this == AppTheme.OCEAN && !systemDark -> OceanLightGoalFrame
    else -> scheme.tertiary
}

/** Tint of badge tier 1..4 (index 0 = Bronze), see [BadgeTierColorsLight] / [BadgeTierColorsDark]. */
val LocalBadgeTierColors = staticCompositionLocalOf { BadgeTierColorsDark }

/** "Gaming dunkel" is always dark; the other themes follow the system dark mode. */
fun AppTheme.isDark(systemDark: Boolean): Boolean = this == AppTheme.GAMING_DARK || systemDark

fun AppTheme.colorScheme(systemDark: Boolean): ColorScheme = when (this) {
    AppTheme.GAMING_DARK -> GamingDarkScheme
    AppTheme.OCEAN -> if (systemDark) OceanDarkScheme else OceanLightScheme
    AppTheme.SUNNY -> if (systemDark) SunnyDarkScheme else SunnyLightScheme
}

private fun AppTheme.answerColors(systemDark: Boolean): AnswerColors = when (this) {
    AppTheme.GAMING_DARK -> GamingAnswerColors
    AppTheme.OCEAN -> if (systemDark) OceanDarkAnswerColors else OceanLightAnswerColors
    AppTheme.SUNNY -> if (systemDark) SunnyDarkAnswerColors else SunnyLightAnswerColors
}

private fun AppTheme.figureAccent(scheme: ColorScheme): Color =
    if (this == AppTheme.GAMING_DARK) GamingFigureAccent else scheme.tertiary

/** Dynamic colour is intentionally not used: the chosen theme always wins. */
@Composable
fun VocTrainerTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = theme.isDark(systemDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val colorScheme = theme.colorScheme(systemDark)
    CompositionLocalProvider(
        LocalAnswerColors provides theme.answerColors(systemDark),
        LocalLevelKindColors provides theme.levelKindColors(systemDark),
        LocalFigureAccent provides theme.figureAccent(colorScheme),
        LocalGoalFrameColor provides theme.goalFrameColor(systemDark, colorScheme),
        LocalBadgeTierColors provides if (dark) BadgeTierColorsDark else BadgeTierColorsLight
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
