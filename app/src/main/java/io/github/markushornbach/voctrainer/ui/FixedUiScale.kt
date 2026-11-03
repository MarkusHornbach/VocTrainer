package io.github.markushornbach.voctrainer.ui

import android.content.res.Configuration
import android.util.DisplayMetrics
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fixed UI scale: the app ignores the system settings "Schriftgröße" (font size) and "Anzeigegröße"
 * (display size) and always renders as with both on their default step, so every screen (above all the
 * typing question above the keyboard) looks the same on every phone.
 *
 * [io.github.markushornbach.voctrainer.MainActivity] applies [overrideFor] as its override configuration, so Compose
 * (`LocalDensity`, `LocalConfiguration`), the resources and the View-based dialogs (e.g. the time picker)
 * all use the same values. Everything else of the system configuration (language, dark mode, orientation, …)
 * stays. The application context keeps the system values (notifications). Changing a system setting
 * recreates the activity (no `configChanges` in the manifest), which applies the override again.
 * The keyboard and the system bars are other windows and keep following the system.
 *
 * Switch it off with [ENABLED].
 */
object FixedUiScale {
    const val ENABLED = true

    /** The default step of "Schriftgröße". */
    const val FONT_SCALE = 1f

    /**
     * The changes to the [system] configuration: font scale [FONT_SCALE] and the device's default density
     * ([DisplayMetrics.DENSITY_DEVICE_STABLE], independent of "Anzeigegröße"); the screen sizes in dp follow
     * the new density, and so does the screen size class. All other fields stay undefined, i.e. they keep the
     * system values. Null if off.
     */
    fun overrideFor(system: Configuration): Configuration? {
        if (!ENABLED) return null
        val stable = DisplayMetrics.DENSITY_DEVICE_STABLE
        val from = system.densityDpi
        return Configuration().apply {
            fontScale = FONT_SCALE
            densityDpi = stable
            if (from != Configuration.DENSITY_DPI_UNDEFINED && from != stable) {
                screenWidthDp = scaledDp(system.screenWidthDp, from, stable, Configuration.SCREEN_WIDTH_DP_UNDEFINED)
                screenHeightDp = scaledDp(system.screenHeightDp, from, stable, Configuration.SCREEN_HEIGHT_DP_UNDEFINED)
                smallestScreenWidthDp = scaledDp(
                    system.smallestScreenWidthDp, from, stable, Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED
                )
                // The size class depends on the dp size; dialog themes, for example, are wider on "large".
                if (screenWidthDp > 0 && screenHeightDp > 0 && system.screenLayout != Configuration.SCREENLAYOUT_UNDEFINED) {
                    screenLayout = (system.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK.inv()) or
                        screenSize(screenWidthDp, screenHeightDp)
                }
            }
        }
    }

    /** A size of [dp] at density [fromDpi] in dp at density [toDpi] (same pixels); [undefined] stays. */
    fun scaledDp(dp: Int, fromDpi: Int, toDpi: Int, undefined: Int = 0): Int =
        if (dp == undefined) dp else (dp.toFloat() * fromDpi / toDpi).roundToInt()

    /** The screen size class (`Configuration.SCREENLAYOUT_SIZE_*`) of a screen of [widthDp] × [heightDp], as Android computes it. */
    fun screenSize(widthDp: Int, heightDp: Int): Int {
        val long = maxOf(widthDp, heightDp)
        val short = minOf(widthDp, heightDp)
        return when {
            long >= 960 && short >= 720 -> Configuration.SCREENLAYOUT_SIZE_XLARGE
            long >= 640 && short >= 480 -> Configuration.SCREENLAYOUT_SIZE_LARGE
            long >= 470 && short >= 320 -> Configuration.SCREENLAYOUT_SIZE_NORMAL
            else -> Configuration.SCREENLAYOUT_SIZE_SMALL
        }
    }

    /**
     * The read-only line under "Anzeige" in the test tools, e.g.
     * „System: Schrift 1,00 · Dichte 420 dpi (Standard 420 dpi) · App: Schrift 1,00 · Dichte 420 dpi“.
     */
    fun testToolsLine(systemFontScale: Float, systemDpi: Int, defaultDpi: Int, appFontScale: Float, appDpi: Int): String {
        fun scale(value: Float) = String.format(Locale.GERMANY, "%.2f", value)
        return "System: Schrift ${scale(systemFontScale)} · Dichte $systemDpi dpi (Standard $defaultDpi dpi) · " +
            "App: Schrift ${scale(appFontScale)} · Dichte $appDpi dpi"
    }
}
