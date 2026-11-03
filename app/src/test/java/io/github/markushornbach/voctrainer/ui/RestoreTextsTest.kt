package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.BackupBook
import io.github.markushornbach.voctrainer.data.BackupInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoreTextsTest {
    @Test
    fun warnsOnlyWhenTheFileComesFromTheOtherApp() {
        assertNull(RestoreTexts.variantWarning("release", debugApp = false))
        assertNull(RestoreTexts.variantWarning("debug", debugApp = true))
        assertNull(RestoreTexts.variantWarning(null, debugApp = false))
        assertNull(RestoreTexts.variantWarning("other", debugApp = true))
        assertEquals("Achtung: Diese Sicherung stammt aus der Test-App.", RestoreTexts.variantWarning("debug", debugApp = false))
        assertEquals("Achtung: Diese Sicherung stammt aus der normalen App.", RestoreTexts.variantWarning("release", debugApp = true))
    }

    @Test
    fun resultMentionsTheSettingsOnlyIfTheFileHadThem() {
        assertEquals(
            "Sicherung wiederhergestellt: Buch (12 Vokabeln, 3 Verben).",
            RestoreTexts.result(BackupInfo(listOf(BackupBook("Buch", 12, 3)), hasSettings = false, appVariant = null))
        )
        assertEquals(
            "Sicherung wiederhergestellt: Buch (12 Vokabeln, 3 Verben), Einstellungen übernommen.",
            RestoreTexts.result(BackupInfo(listOf(BackupBook("Buch", 12, 3)), hasSettings = true, appVariant = "release"))
        )
    }

    @Test
    fun restoreNamesTheEntriesIgnoredWithoutCoreWord() {
        assertEquals(
            "Sicherung wiederhergestellt: Buch (10 Vokabeln, 3 Verben). 2 Vokabeln ohne Wort zum Tippen wurden ignoriert. " +
                "🧪 Du findest sie in „Vokabeln editieren“.",
            RestoreTexts.result(
                BackupInfo(listOf(BackupBook("Buch", 10, 3)), hasSettings = false, appVariant = null, ignoredWithoutCoreWord = 2)
            )
        )
    }

    @Test
    fun releaseVariantDropsThePointerToTheEditor() {
        assertEquals(
            " 2 Vokabeln ohne Wort zum Tippen wurden ignoriert.",
            CoreWordTexts.ignoredSuffix(2, pointToEditor = false)
        )
        assertEquals("1 Vokabel ohne Wort zum Tippen wurde ignoriert.", CoreWordTexts.ignored(1, pointToEditor = false))
        assertEquals("", CoreWordTexts.ignoredSuffix(0, pointToEditor = false))
    }
}
