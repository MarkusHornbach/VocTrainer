package io.github.markushornbach.voctrainer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BackupFileNameTest {
    @Test
    fun theDateComesFirstAndTheTestAppAddsDebug() {
        val date = LocalDate.of(2026, 10, 17)
        assertEquals("261017_voctrainer-sicherung.json", backupFileName(date, debug = false))
        assertEquals("261017_debug_voctrainer-sicherung.json", backupFileName(date, debug = true))
    }
}
