package io.github.markushornbach.voctrainer.ui

/** One version of the page "Versionshinweise". [date] is null if the heading has no readable date. */
data class ReleaseNotesSection(val version: String, val date: String?, val bullets: List<String>)

/** Reads the format of `RELEASE_NOTES.md`: sections `## Version X.Y.Z – DD.MM.YYYY` with bullets `- …`. */
object ReleaseNotesParser {
    private val heading = Regex("""^##\s+Version\s+(\S+)(?:\s+\S+\s+(\d{2}\.\d{2}\.\d{4}))?.*$""")
    private val bullet = Regex("""^[-*]\s+(.+)$""")

    /** The sections in the order of the file (newest first). Unknown lines are ignored, so this never fails. */
    fun parse(text: String): List<ReleaseNotesSection> {
        val sections = mutableListOf<ReleaseNotesSection>()
        text.removePrefix("﻿").lineSequence().map(String::trim).forEach { line ->
            val section = heading.matchEntire(line)
            val item = bullet.matchEntire(line)
            when {
                section != null -> sections += ReleaseNotesSection(
                    version = section.groupValues[1],
                    date = section.groupValues[2].ifEmpty { null },
                    bullets = emptyList()
                )
                item != null && sections.isNotEmpty() -> {
                    val last = sections.removeAt(sections.lastIndex)
                    sections += last.copy(bullets = last.bullets + item.groupValues[1].trim())
                }
            }
        }
        return sections
    }
}

/** What to do with the page "Versionshinweise" when the start screen is reached. */
enum class ReleaseNotesAction {
    NOTHING,

    /** Remember the running version without showing anything. */
    STORE,

    /** Open the page once and remember the running version. */
    SHOW
}

object ReleaseNotesRules {
    /**
     * [lastShown] is the stored version, null if none. Nothing stored after the first start means an
     * update from a version without this page; only during the first start it is a fresh installation.
     * During the first start a stored older version waits until it is over.
     */
    fun decide(lastShown: String?, running: String, firstStartDone: Boolean, hasSection: Boolean): ReleaseNotesAction = when {
        lastShown == running -> ReleaseNotesAction.NOTHING
        !firstStartDone -> if (lastShown == null) ReleaseNotesAction.STORE else ReleaseNotesAction.NOTHING
        hasSection -> ReleaseNotesAction.SHOW
        else -> ReleaseNotesAction.STORE
    }
}

/** Texts of the page "Versionshinweise" and its entry in the settings. */
object ReleaseNotesTexts {
    const val TITLE = "Versionshinweise"
    const val OPEN_LABEL = "Versionshinweise öffnen"
    const val EMPTY = "Gerade gibt es hier nichts zu lesen."

    fun versionTitle(version: String) = "Version $version"
}
