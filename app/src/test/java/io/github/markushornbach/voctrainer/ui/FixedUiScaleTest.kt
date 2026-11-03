package io.github.markushornbach.voctrainer.ui

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FixedUiScaleTest {
    @Test
    fun theFontScaleIsTheDefaultStep() {
        assertEquals(1f, FixedUiScale.FONT_SCALE)
    }

    @Test
    fun screenSizesInDpFollowTheDensity() {
        // A 1080 px, 420 dpi phone: 1080 px wide, default 420 dpi = 411 dp; largest display size 540 dpi = 320 dp.
        assertEquals(411, FixedUiScale.scaledDp(320, fromDpi = 540, toDpi = 420))
        assertEquals(320, FixedUiScale.scaledDp(411, fromDpi = 420, toDpi = 540))
        assertEquals(411, FixedUiScale.scaledDp(411, fromDpi = 420, toDpi = 420))
        assertEquals(0, FixedUiScale.scaledDp(0, fromDpi = 540, toDpi = 420, undefined = 0))
    }

    @Test
    fun theScreenSizeClassFollowsTheDpSize() {
        // A 1080 px, 420 dpi phone at the default density, and at the smallest display size (484 dp wide, "large").
        assertEquals(Configuration.SCREENLAYOUT_SIZE_NORMAL, FixedUiScale.screenSize(411, 914))
        assertEquals(Configuration.SCREENLAYOUT_SIZE_LARGE, FixedUiScale.screenSize(484, 1075))
        assertEquals(Configuration.SCREENLAYOUT_SIZE_NORMAL, FixedUiScale.screenSize(711, 320))
        assertEquals(Configuration.SCREENLAYOUT_SIZE_SMALL, FixedUiScale.screenSize(300, 460))
        assertEquals(Configuration.SCREENLAYOUT_SIZE_XLARGE, FixedUiScale.screenSize(800, 1280))
    }

    @Test
    fun theTestToolsLineShowsSystemDefaultAndApp() {
        assertEquals(
            "System: Schrift 1,00 · Dichte 420 dpi (Standard 420 dpi) · App: Schrift 1,00 · Dichte 420 dpi",
            FixedUiScale.testToolsLine(1f, 420, 420, 1f, 420)
        )
        assertEquals(
            "System: Schrift 1,30 · Dichte 540 dpi (Standard 420 dpi) · App: Schrift 1,00 · Dichte 420 dpi",
            FixedUiScale.testToolsLine(1.3f, 540, 420, 1f, 420)
        )
    }
}
