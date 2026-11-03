package io.github.markushornbach.voctrainer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Hand-written Material 3 schemes. Where a key colour from the design had too little contrast for
// text, a darker/lighter tone of the same hue is used for that role (checked for ≥ 4.5:1).

/** "Gaming dunkel": always dark; deep violet, turquoise accent. */
val GamingDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7F77DD),
    onPrimary = Color(0xFF15103F),
    primaryContainer = Color(0xFF3C3489),
    onPrimaryContainer = Color(0xFFEEEDFE),
    inversePrimary = Color(0xFF4F46B8),
    secondary = Color(0xFF5DCAA5),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF1E5D4B),
    onSecondaryContainer = Color(0xFFC8F5E4),
    tertiary = Color(0xFF5DCAA5),
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = Color(0xFF1E5D4B),
    onTertiaryContainer = Color(0xFFC8F5E4),
    background = Color(0xFF26215C),
    onBackground = Color(0xFFEEEDFE),
    surface = Color(0xFF26215C),
    onSurface = Color(0xFFEEEDFE),
    surfaceVariant = Color(0xFF3C3489),
    onSurfaceVariant = Color(0xFFCBC7F5),
    surfaceTint = Color(0xFF7F77DD),
    inverseSurface = Color(0xFFEEEDFE),
    inverseOnSurface = Color(0xFF2E2870),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFFD93A2B),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF9590D0),
    outlineVariant = Color(0xFF4A4398),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3478),
    surfaceDim = Color(0xFF1E1A4D),
    surfaceContainerLowest = Color(0xFF1A1648),
    surfaceContainerLow = Color(0xFF2B2565),
    surfaceContainer = Color(0xFF2F296C),
    surfaceContainerHigh = Color(0xFF373079),
    surfaceContainerHighest = Color(0xFF3C3489)
)

/** "Ozean" light: blue, light blue background, orange accent. */
val OceanLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF185FA5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    inversePrimary = Color(0xFFA2C9FF),
    secondary = Color(0xFF8A5200),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB3),
    onSecondaryContainer = Color(0xFF2A1700),
    tertiary = Color(0xFFB86E00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEF9F27),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFE6F1FB),
    onBackground = Color(0xFF0B1D2E),
    surface = Color(0xFFE6F1FB),
    onSurface = Color(0xFF0B1D2E),
    surfaceVariant = Color(0xFFD5E2F0),
    onSurfaceVariant = Color(0xFF3F4B58),
    surfaceTint = Color(0xFF185FA5),
    inverseSurface = Color(0xFF213242),
    inverseOnSurface = Color(0xFFE8F1FA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7C89),
    outlineVariant = Color(0xFFBFCAD6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF3F8FD),
    surfaceDim = Color(0xFFCCDCEB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5FC),
    surfaceContainer = Color(0xFFDCE9F6),
    surfaceContainerHigh = Color(0xFFD3E3F3),
    surfaceContainerHighest = Color(0xFFCADCEF)
)

/** "Ozean" dark variant (system dark mode). */
val OceanDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA2C9FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF0F4A82),
    onPrimaryContainer = Color(0xFFD3E4FF),
    inversePrimary = Color(0xFF185FA5),
    secondary = Color(0xFFFFB95C),
    onSecondary = Color(0xFF462A00),
    secondaryContainer = Color(0xFF6A4000),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = Color(0xFFEF9F27),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF6A4000),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0E1A26),
    onBackground = Color(0xFFDCE6F0),
    surface = Color(0xFF0E1A26),
    onSurface = Color(0xFFDCE6F0),
    surfaceVariant = Color(0xFF2C3A48),
    onSurfaceVariant = Color(0xFFBCC8D5),
    surfaceTint = Color(0xFFA2C9FF),
    inverseSurface = Color(0xFFDCE6F0),
    inverseOnSurface = Color(0xFF22303D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF86929F),
    outlineVariant = Color(0xFF3C4855),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF344250),
    surfaceDim = Color(0xFF0E1A26),
    surfaceContainerLowest = Color(0xFF091520),
    surfaceContainerLow = Color(0xFF16222F),
    surfaceContainer = Color(0xFF1A2734),
    surfaceContainerHigh = Color(0xFF24313F),
    surfaceContainerHighest = Color(0xFF2F3C4A)
)

/** "Sonnig" light: orange, sand background, dark brown text. */
val SunnyLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFB8481F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3A0B00),
    inversePrimary = Color(0xFFFFB59A),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDEA8),
    onSecondaryContainer = Color(0xFF2C1800),
    tertiary = Color(0xFF8F6300),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF2C1800),
    background = Color(0xFFFAEEDA),
    onBackground = Color(0xFF412402),
    surface = Color(0xFFFAEEDA),
    onSurface = Color(0xFF412402),
    surfaceVariant = Color(0xFFF1DFC6),
    onSurfaceVariant = Color(0xFF5A4330),
    surfaceTint = Color(0xFFD85A30),
    inverseSurface = Color(0xFF3A2A1A),
    inverseOnSurface = Color(0xFFFBEFE0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8D735E),
    outlineVariant = Color(0xFFDCC5AE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F0),
    surfaceDim = Color(0xFFE6D6BE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF4E6),
    surfaceContainer = Color(0xFFF6E6CC),
    surfaceContainerHigh = Color(0xFFF1DFC3),
    surfaceContainerHighest = Color(0xFFEBD8BA)
)

/** "Sonnig" dark variant (system dark mode). */
val SunnyDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF5C1A00),
    primaryContainer = Color(0xFF8A3417),
    onPrimaryContainer = Color(0xFFFFDBCC),
    inversePrimary = Color(0xFFB8481F),
    secondary = Color(0xFFF2BF6B),
    onSecondary = Color(0xFF422C00),
    secondaryContainer = Color(0xFF5F4100),
    onSecondaryContainer = Color(0xFFFFDEA8),
    tertiary = Color(0xFFF2BF6B),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDEA8),
    background = Color(0xFF1E140C),
    onBackground = Color(0xFFF1E0CF),
    surface = Color(0xFF1E140C),
    onSurface = Color(0xFFF1E0CF),
    surfaceVariant = Color(0xFF4A3A2C),
    onSurfaceVariant = Color(0xFFD8C3AF),
    surfaceTint = Color(0xFFFFB59A),
    inverseSurface = Color(0xFFF1E0CF),
    inverseOnSurface = Color(0xFF3A2A1E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D7B),
    outlineVariant = Color(0xFF52433A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF463A2E),
    surfaceDim = Color(0xFF1E140C),
    surfaceContainerLowest = Color(0xFF180F08),
    surfaceContainerLow = Color(0xFF261B12),
    surfaceContainer = Color(0xFF2A1F16),
    surfaceContainerHigh = Color(0xFF352920),
    surfaceContainerHighest = Color(0xFF40342A)
)

// Green for correct answers (red comes from each scheme's error colours).
val CorrectContainerLight = Color(0xFFB8F0C4)
val OnCorrectContainerLight = Color(0xFF00210D)
val CorrectContainerDark = Color(0xFF1E5230)
val OnCorrectContainerDark = Color(0xFFB8F0C4)

// Badge tiers Bronze, Silber, Gold, Platin (icon tint). Light themes use deep tones (≥ 4.5:1 on the
// light blue and sand backgrounds), dark themes bright ones (≥ 4.5:1 on the dark and violet backgrounds).
val BadgeTierColorsLight = listOf(Color(0xFF8C4A12), Color(0xFF4F5B6B), Color(0xFF8A6400), Color(0xFF2E6F8E))
val BadgeTierColorsDark = listOf(Color(0xFFE5A374), Color(0xFFD3D9E1), Color(0xFFF2C94C), Color(0xFFA6E3F2))

// On deep violet the dark green would be too faint; a brighter green with dark text is used.
val CorrectContainerGaming = Color(0xFF34A865)
val OnCorrectContainerGaming = Color(0xFF00210D)

// Test orange: marks everything that exists only in the test app (debug build). One fixed colour for all
// three colour themes, light and dark (not part of a scheme). It reaches about 3:1 or more on every page
// background (light ones ~3.1:1, dark ones ~4.9:1+); a darker orange would be too faint on the dark themes.
val TestOrange = Color(0xFFE8590C)

// Text on a filled orange bar (about 5.5:1).
val OnTestOrange = Color(0xFF1A1A1A)
