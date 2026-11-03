package io.github.markushornbach.voctrainer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class VersionTextTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val buildTime = ZonedDateTime.of(2026, 10, 8, 23, 40, 0, 0, berlin).toInstant().toEpochMilli()

    @Test
    fun debugBuildShowsVersionDateAndDebug() {
        assertEquals(
            listOf("Version 0.2.0+3 (abc1234)", "Erstellt am 08.10.2026 23:40 · Debug"),
            VersionText.lines("0.2.0+3 (abc1234)", buildTime, debug = true, zone = berlin)
        )
    }

    @Test
    fun releaseBuildHasNoDebugMarkAndUsesTheDeviceTimeZone() {
        assertEquals(
            listOf("Version 0.2.0", "Erstellt am 08.10.2026 21:40"),
            VersionText.lines("0.2.0", buildTime, debug = false, zone = ZoneId.of("UTC"))
        )
    }
}
