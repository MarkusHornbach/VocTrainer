package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.BackupBook
import io.github.markushornbach.voctrainer.data.BackupInfo
import io.github.markushornbach.voctrainer.data.BookImport
import io.github.markushornbach.voctrainer.data.VocabularyImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTextsTest {
    private val counts = VocabularyImportResult(vocabularyImported = 10, vocabularySkipped = 0, verbsAdded = 2, verbsUpdated = 0)

    @Test
    fun importMessageNamesTheBook() {
        assertEquals(
            "Neues Buch „Englisch Klasse 7“ angelegt. 10 Vokabeln importiert, 0 schon vorhanden, " +
                "2 unregelmäßige Verben hinzugefügt, 0 Verben aktualisiert.",
            importResultText(BookImport(counts, "englisch-klasse-7", "Englisch Klasse 7", isNewBook = true, isActive = false))
        )
        assertEquals(
            "Buch „Testbuch 6“: 10 Vokabeln importiert, 0 schon vorhanden, " +
                "2 unregelmäßige Verben hinzugefügt, 0 Verben aktualisiert.",
            importResultText(BookImport(counts, "testbuch-6", "Testbuch 6", isNewBook = false, isActive = false))
        )
    }

    @Test
    fun importMessageNamesChangedIgnoredAndRestoredEntries() {
        val changed = counts.copy(vocabularySkipped = 5, vocabularyUpdated = 2, vocabularyExcluded = 1, vocabularyRestored = 1)
        assertEquals(
            "Buch „Klasse 6“: 10 Vokabeln importiert, 5 schon vorhanden (davon 2 geändert, 1 jetzt ignoriert, 1 wieder aktiv), " +
                "2 unregelmäßige Verben hinzugefügt, 0 Verben aktualisiert.",
            importResultText(BookImport(changed, "k6", "Klasse 6", isNewBook = false, isActive = true))
        )
        assertEquals(
            "Buch „Klasse 6“: 10 Vokabeln importiert, 5 schon vorhanden (davon 1 wieder aktiv), " +
                "2 unregelmäßige Verben hinzugefügt, 0 Verben aktualisiert.",
            importResultText(BookImport(counts.copy(vocabularySkipped = 5, vocabularyRestored = 1), "k6", "Klasse 6", isNewBook = false, isActive = true))
        )
    }

    @Test
    fun switchQuestionAndBookList() {
        assertEquals("Neues Buch „Testbuch“ – jetzt zu diesem Buch wechseln?", BookTexts.switchQuestion("Testbuch"))
        assertEquals("120 Wörter, 35 gelernt", BookTexts.counts(120, 35))
        assertEquals("1 Wort, 0 gelernt", BookTexts.counts(1, 0))
    }

    private val class6 = BackupBook("Testbuch 6", 198, 51)
    private val class7 = BackupBook("Englisch Klasse 7", 30, 4)

    @Test
    fun restoreDialogListsEveryBookWithItsContentsAndNoBookCount() {
        assertEquals(
            "Die Sicherung enthält:\n• Testbuch 6: 198 Vokabeln, 51 Verben",
            RestoreTexts.contents(BackupInfo(listOf(class6), hasSettings = true, appVariant = null))
        )
        val two = RestoreTexts.contents(BackupInfo(listOf(class6, class7), hasSettings = true, appVariant = null))
        assertEquals(
            "Die Sicherung enthält:\n• Testbuch 6: 198 Vokabeln, 51 Verben\n• Englisch Klasse 7: 30 Vokabeln, 4 Verben",
            two
        )
        assertFalse(two.contains("Bücher"))
        assertEquals(
            "Die Sicherung enthält kein Buch, nur Einstellungen.",
            RestoreTexts.contents(BackupInfo(emptyList(), hasSettings = true, appVariant = null))
        )
        assertEquals(
            "Die Sicherung enthält kein Buch.",
            RestoreTexts.contents(BackupInfo(emptyList(), hasSettings = false, appVariant = null))
        )
    }

    @Test
    fun restoreConfirmationSaysTheExampleBookIsNotInTheBackup() {
        assertTrue(RestoreTexts.CONFIRM.endsWith("Das Beispielbuch ist nicht in der Sicherung und beginnt danach neu."))
    }

    @Test
    fun restoreResultNamesTheBooks() {
        assertEquals(
            "Sicherung wiederhergestellt: Testbuch 6 (198 Vokabeln, 51 Verben), Einstellungen übernommen.",
            RestoreTexts.result(BackupInfo(listOf(class6), hasSettings = true, appVariant = null))
        )
        assertEquals(
            "Sicherung wiederhergestellt: Testbuch 6 (198 Vokabeln, 51 Verben), Englisch Klasse 7 (30 Vokabeln, 4 Verben).",
            RestoreTexts.result(BackupInfo(listOf(class6, class7), hasSettings = false, appVariant = null))
        )
        assertEquals(
            "Sicherung wiederhergestellt: kein Buch, Einstellungen übernommen.",
            RestoreTexts.result(BackupInfo(emptyList(), hasSettings = true, appVariant = null))
        )
    }
}
