package io.github.markushornbach.voctrainer.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Text of the version block in the settings, e.g. "Version 0.2.0+3 (abc1234)". */
object VersionText {
    private val dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun lines(versionName: String, buildTime: Long, debug: Boolean, zone: ZoneId): List<String> = listOf(
        "Version $versionName",
        "Erstellt am " + dateTime.format(Instant.ofEpochMilli(buildTime).atZone(zone)) + if (debug) " · Debug" else ""
    )
}
