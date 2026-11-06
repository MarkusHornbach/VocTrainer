package io.github.markushornbach.voctrainer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseNotesTest {
    private val file = """
        # Versionshinweise

        ## Version 1.1.0 – 05.12.2026
        - Neu: „Mein Avatar“ tanzt.
        - Du kannst mehr üben.

        ## Version 1.1.0-rc1 – 01.12.2026
        - Probe

        ## Version 1.0.0 – 03.11.2026
        - Erste Version
    """.trimIndent()

    @Test
    fun readsVersionsDatesAndBullets() {
        val sections = ReleaseNotesParser.parse(file)
        assertEquals(
            ReleaseNotesSection("1.1.0", "05.12.2026", listOf("Neu: „Mein Avatar“ tanzt.", "Du kannst mehr üben.")),
            sections[0]
        )
    }

    @Test
    fun keepsTheOrderOfTheFileAndShowsCandidatesLikeOtherSections() {
        assertEquals(listOf("1.1.0", "1.1.0-rc1", "1.0.0"), ReleaseNotesParser.parse(file).map { it.version })
        assertEquals(listOf("Probe"), ReleaseNotesParser.parse(file)[1].bullets)
    }

    @Test
    fun emptyOrUnreadableFilesGiveNoSections() {
        assertEquals(emptyList<ReleaseNotesSection>(), ReleaseNotesParser.parse(""))
        assertEquals(emptyList<ReleaseNotesSection>(), ReleaseNotesParser.parse("# Versionshinweise\n\nNur Text\n- ohne Abschnitt"))
        assertEquals(emptyList<ReleaseNotesSection>(), ReleaseNotesParser.parse("## Version\n## Änderungen\n\u0000"))
    }

    @Test
    fun unknownLinesAreIgnoredAndWhatIsReadableStays() {
        val sections = ReleaseNotesParser.parse(
            "﻿# Versionshinweise\r\n## Version 2.0.0\r\nirgendein Text\r\n- Punkt\r\n### Mehr\r\n## Version 1.0.0 - kein Datum\r\n* Stern\r\n"
        )
        assertEquals(
            listOf(ReleaseNotesSection("2.0.0", null, listOf("Punkt")), ReleaseNotesSection("1.0.0", null, listOf("Stern"))),
            sections
        )
    }

    @Test
    fun aSectionWithoutBulletsKeepsItsHeading() {
        assertEquals(
            listOf(ReleaseNotesSection("1.0.0", "03.11.2026", emptyList())),
            ReleaseNotesParser.parse("## Version 1.0.0 – 03.11.2026")
        )
    }

    /** The real file of the repository, which the build bundles into the app. */
    @Test
    fun theRepositoryFileHasTheExpectedFormat() {
        val notes = listOf(File("../RELEASE_NOTES.md"), File("RELEASE_NOTES.md")).first { it.isFile }
        val sections = ReleaseNotesParser.parse(notes.readText())
        assertTrue("sections expected", sections.isNotEmpty())
        sections.forEach {
            assertTrue("date of ${it.version}", it.date != null)
            assertTrue("bullets of ${it.version}", it.bullets.isNotEmpty())
        }
    }

    private fun decide(lastShown: String?, running: String = "1.1.0", firstStartDone: Boolean = true, hasSection: Boolean = true) =
        ReleaseNotesRules.decide(lastShown, running, firstStartDone, hasSection)

    @Test
    fun aFreshInstallationShowsNothingAndStoresTheVersion() {
        assertEquals(ReleaseNotesAction.STORE, decide(lastShown = null, firstStartDone = false))
    }

    @Test
    fun theSameVersionDoesNothing() {
        assertEquals(ReleaseNotesAction.NOTHING, decide(lastShown = "1.1.0"))
        assertEquals(ReleaseNotesAction.NOTHING, decide(lastShown = "1.1.0", firstStartDone = false))
    }

    @Test
    fun aNewVersionShowsThePageAlsoForAPatchVersion() {
        assertEquals(ReleaseNotesAction.SHOW, decide(lastShown = "1.0.0"))
        assertEquals(ReleaseNotesAction.SHOW, decide(lastShown = "1.0.0", running = "1.0.1"))
    }

    @Test
    fun theFirstUpdateFromAVersionWithoutThePageShowsIt() {
        assertEquals(ReleaseNotesAction.SHOW, decide(lastShown = null))
    }

    @Test
    fun noSectionForTheRunningVersionStoresWithoutShowing() {
        assertEquals(ReleaseNotesAction.STORE, decide(lastShown = "1.0.0", running = "1.0.0+3 (abc1234)", hasSection = false))
        assertEquals(ReleaseNotesAction.STORE, decide(lastShown = null, hasSection = false))
    }

    @Test
    fun anOlderVersionWaitsForTheEndOfTheFirstStart() {
        assertEquals(ReleaseNotesAction.NOTHING, decide(lastShown = "1.0.0", firstStartDone = false))
    }
}
