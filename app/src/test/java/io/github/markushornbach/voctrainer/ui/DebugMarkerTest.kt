package io.github.markushornbach.voctrainer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.markushornbach.voctrainer.ui.theme.GamingDarkScheme
import io.github.markushornbach.voctrainer.ui.theme.OceanDarkScheme
import io.github.markushornbach.voctrainer.ui.theme.OceanLightScheme
import io.github.markushornbach.voctrainer.ui.theme.OnTestOrange
import io.github.markushornbach.voctrainer.ui.theme.SunnyDarkScheme
import io.github.markushornbach.voctrainer.ui.theme.SunnyLightScheme
import io.github.markushornbach.voctrainer.ui.theme.TestOrange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared marker of the test app: label prefix and the contrast of the test orange. */
class DebugMarkerTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun labelGetsTheTestPrefix() {
        assertEquals("🧪 Vokabel hinzufügen", DebugMarker.label("Vokabel hinzufügen"))
        assertEquals("🧪 Nur Test-App", DebugMarker.BAR_TEXT)
    }

    @Test
    fun testOrangeStandsOutOnEveryThemeBackground() {
        listOf(GamingDarkScheme, OceanLightScheme, OceanDarkScheme, SunnyLightScheme, SunnyDarkScheme).forEach { scheme ->
            val ratio = contrast(TestOrange, scheme.background)
            assertTrue("test orange on ${scheme.background}: $ratio", ratio >= 3f)
        }
    }

    @Test
    fun barTextIsReadableOnTestOrange() {
        assertTrue(contrast(OnTestOrange, TestOrange) >= 4.5f)
    }
}
