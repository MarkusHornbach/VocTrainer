package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.exampleBookJson
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.QuizFilterCodec
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.SettingsBackup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The head of an import file of version 2, with its book. */
private const val IMPORT = """"format": "voctrainer-import", "version": 2, "bookId": "b", "bookTitle": "Buch""""

/** The head of a sync file of version 5 with the one book "b". */
private const val SYNC = """"format": "voctrainer-sync", "version": 5, "books": [{"id": "b", "title": "Buch", "isActive": true}]"""

class VocTrainerJsonTest {
    /** The example book (see [exampleBookJson]). */
    private val exampleFile = exampleBookJson()

    private val book = Book("b", "Buch", "en", isActive = true)

    private fun Vocabulary.inBook() = copy(bookId = book.id)

    private fun IrregularVerb.inBook() = copy(bookId = book.id)

    /** A sync file of the one [book]; the records must carry its id. */
    private fun encode(
        vocabulary: List<Vocabulary>, verbs: List<IrregularVerb>, settings: SettingsBackup? = null, appVariant: String? = null
    ) = VocTrainerJson.encodeSync(listOf(book), vocabulary, verbs, settings = settings, appVariant = appVariant)

    @Test
    fun parsesExampleFile() {
        val file = VocTrainerJson.parseImport(exampleFile)

        assertEquals("beispielbuch", file.bookId)
        assertEquals("Beispielbuch Englisch", file.bookTitle)
        assertEquals(150, file.entries.size)
        assertEquals(36, file.irregularVerbs.size)
        val first = file.entries.first()
        assertEquals("pet", first.english)
        assertEquals(listOf("Haustier"), first.german)
        assertEquals(1, first.unit)
        assertEquals("Our new pet", first.unitTitle)
        assertNull(first.part)
        assertEquals(150, first.page)
        assertEquals("pet", first.coreWord)
        assertTrue(file.entries.all { !it.coreWord.isNullOrBlank() && it.excluded != true })
    }

    private fun invalidFile(name: String): String = javaClass.getResource("/import/$name")!!.readText()

    /** Case G3: a file with entries without core word is rejected as a whole. */
    @Test
    fun exampleEntriesWithoutCoreWordsAreRejected() {
        val error = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport(invalidFile("beispielbuch_ohne_coreword.json"))
        }
        assertEquals(
            "In der Datei fehlt bei 7 Vokabeln das Wort zum Tippen (coreWord): „pet“, „animal“, " +
                "„animal shelter“, „rabbit“, „guinea pig“ und 2 weitere. Bitte die Importdatei neu erstellen.",
            error.message
        )
    }

    /** Cases G1, G2, G4: no book, version 1, broken JSON. */
    @Test
    fun invalidExampleFilesAreRejected() {
        val noBook = "Diese Datei gehört zu keinem Buch (Buch-Kennung fehlt). Bitte eine aktuelle Vokabeldatei verwenden."
        val tooOld = "Diese Datei stammt aus einer zu alten App-Version und kann nicht gelesen werden."
        for ((name, expected) in listOf(
            "beispielbuch_ohne_buch.json" to noBook,
            "beispielbuch_version1.json" to tooOld,
            "beispielbuch_kaputt.json" to "Die Datei ist keine gültige JSON-Datei."
        )) {
            val error = assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseImport(invalidFile(name)) }
            assertEquals(name, expected, error.message)
        }
    }

    @Test
    fun importFileNamesItsBook() {
        val file = VocTrainerJson.parseImport(
            """{"format": "voctrainer-import", "version": 2, "bookId": " englisch-klasse-7 ", "bookTitle": " Klasse 7 ",
                "entries": [{"english": "dog", "german": ["Hund"], "unit": 1, "coreWord": "dog"}]}"""
        )
        assertEquals("englisch-klasse-7", file.bookId)
        assertEquals("Klasse 7", file.bookTitle)
        // Without "language" the book is English.
        assertEquals("en", file.language)
        val spanish = VocTrainerJson.parseImport("""{$IMPORT, "language": "es", "entries": []}""")
        assertEquals("es", spanish.language)
    }

    @Test
    fun importFileWithoutBookIsRejected() {
        val expected = "Diese Datei gehört zu keinem Buch (Buch-Kennung fehlt). Bitte eine aktuelle Vokabeldatei verwenden."
        for (text in listOf(
            """{"format": "voctrainer-import", "version": 2, "bookTitle": "Buch", "entries": []}""",
            """{"format": "voctrainer-import", "version": 2, "bookId": "  ", "bookTitle": "Buch", "entries": []}"""
        )) {
            assertEquals(text, expected, assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseImport(text) }.message)
        }
        // Version 1 is too old, also with a book added by hand.
        for (text in listOf(
            """{"format": "voctrainer-import", "version": 1, "bookId": "b", "bookTitle": "Buch", "entries": []}""",
            """{"format": "voctrainer-import", "version": 1, "entries": []}"""
        )) {
            val message = assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseImport(text) }.message
            assertEquals(text, VocTrainerJson.OLD_VERSION_MESSAGE, message)
        }
        val noTitle = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{"format": "voctrainer-import", "version": 2, "bookId": "b", "entries": []}""")
        }
        assertEquals(VocTrainerJson.NO_BOOK_TITLE_MESSAGE, noTitle.message)
    }

    @Test
    fun missingCoreWordRejectsTheFileAndListsTheEntries() {
        fun entries(vararg english: String) = english.joinToString(",") { """{"english": "$it", "german": ["x"], "unit": 1}""" }
        val one = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport(
                """{$IMPORT, "entries": [
                    {"english": "dog", "german": ["Hund"], "unit": 1, "coreWord": "dog"},
                    {"english": "cat", "german": ["Katze"], "unit": 1, "coreWord": " "},
                    {"english": "old", "german": ["alt"], "unit": 1, "excluded": true}]}"""
            )
        }
        assertEquals(
            "In der Datei fehlt bei 1 Vokabel das Wort zum Tippen (coreWord): „cat“. Bitte die Importdatei neu erstellen.",
            one.message
        )
        val three = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{$IMPORT, "entries": [${entries("a", "b", "c")}]}""")
        }
        assertEquals(
            "In der Datei fehlt bei 3 Vokabeln das Wort zum Tippen (coreWord): „a“, „b“, „c“. Bitte die Importdatei neu erstellen.",
            three.message
        )
    }

    @Test
    fun excludedEntriesNeedNoCoreWord() {
        val file = VocTrainerJson.parseImport(
            """{$IMPORT, "entries": [
                {"english": "care for sb.", "german": ["x"], "unit": 1, "excluded": true}]}"""
        )
        assertNull(file.entries.single().coreWord)
    }

    @Test
    fun rejectsWrongFormat() {
        val error = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{"format": "something-else", "version": 1, "entries": []}""")
        }
        assertTrue(error.message!!.contains("voctrainer-import"))
    }

    @Test
    fun rejectsMissingFormat() {
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{"version": 2, "entries": []}""")
        }
    }

    @Test
    fun rejectsInvalidJson() {
        assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseImport("{not json") }
        assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseImport("[]") }
    }

    @Test
    fun ignoresUnknownFieldsAndDefaultsMissingOptionalFields() {
        val file = VocTrainerJson.parseImport(
            """
            {
              $IMPORT, "futureTopLevel": {"a": 1},
              "entries": [{"english": "bag", "german": ["Tasche"], "unit": 1, "coreWord": "bag", "pronunciation": "bæɡ"}],
              "irregularVerbs": [{"infinitive": "(to) go", "simplePast": "went", "german": ["gehen"],
                                  "unit": 1, "audio": null}]
            }
            """.trimIndent()
        )

        val entry = file.entries.single()
        assertEquals("bag", entry.english)
        assertNull(entry.unitTitle)
        assertNull(entry.part)
        assertNull(entry.extra)
        val verb = file.irregularVerbs.single()
        assertNull(verb.pastParticiple)
        assertNull(verb.part)
        assertEquals(false, verb.germanAdded)
    }

    @Test
    fun rejectsMissingRequiredFields() {
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{$IMPORT, "entries": [{"english": "bag", "unit": 1}]}""")
        }
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{$IMPORT, "entries": [{"english": "bag", "german": []}]}""")
        }
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{$IMPORT, "entries": [{"english": " ", "german": ["x"], "unit": 1}]}""")
        }
    }

    @Test
    fun keepsEnglishTextExactly() {
        val file = VocTrainerJson.parseImport(
            """{$IMPORT,
                "entries": [{"english": "(to) care for sb./sth. …", "german": ["jn./etwas pflegen"], "unit": 2,
                             "coreWord": "care"}]}"""
        )
        assertEquals("(to) care for sb./sth. …", file.entries.single().english)
    }

    @Test
    fun importAndSyncFilesPointToTheOtherButton() {
        val syncError = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{"format": "voctrainer-sync", "version": 3}""")
        }
        assertTrue(syncError.message!!.contains("Sicherung wiederherstellen"))
        val importError = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseSync(exampleFile)
        }
        assertTrue(importError.message!!.contains("Vokabeln importieren"))
    }

    @Test
    fun syncRoundTripKeepsAllFields() {
        val vocabulary = listOf(
            Vocabulary(
                id = "v1", english = "bag", germanMeanings = listOf("Tasche", "Tüte"), unit = 1,
                unitTitle = "Holidays", part = "A", extra = "Put the apples in the bag.",
                page = 150, wordType = WordType.NOUN, leitnerLevel = 3, updatedAt = 1234L, isDeleted = false,
                highestLevel = 4
            ),
            Vocabulary(id = "v2", english = "old", germanMeanings = listOf("alt"), updatedAt = 99L, isDeleted = true)
        ).map { it.inBook() }
        val verbs = listOf(
            IrregularVerb(
                id = "i1", infinitive = "(to) catch", simplePast = "caught", pastParticiple = null,
                germanMeanings = listOf("fangen"), unit = 1, part = "B", page = 151, germanAdded = true,
                leitnerLevel = 2, updatedAt = 555L
            ).inBook()
        )

        val parsed = VocTrainerJson.parseSync(encode(vocabulary, verbs))

        assertEquals(vocabulary, parsed.vocabulary)
        assertEquals(verbs, parsed.irregularVerbs)
        assertEquals(listOf(book), parsed.books)
    }

    /** A backup holds every book: records keep their book, the active flag and the per-book selections come back. */
    @Test
    fun backupWithTwoBooksRoundTrips() {
        val unit2 = QuizFilterCodec.encode(QuizFilter(UnitChoice.Number(2), setOf("A")))
        val books = listOf(
            Book("testbuch-6", "Testbuch 6", "en", isActive = false, quizFilter = unit2),
            Book("debug-testbuch", "Testbuch", "en", isActive = true, verbQuizFilter = QuizFilterCodec.encode(QuizFilter()))
        )
        val vocabulary = listOf(
            Vocabulary(id = "v1", english = "bag", germanMeanings = listOf("Tasche"), leitnerLevel = 4, highestLevel = 4, updatedAt = 1L, bookId = "testbuch-6"),
            Vocabulary(id = "v2", english = "apple", germanMeanings = listOf("Apfel"), leitnerLevel = 1, highestLevel = 1, updatedAt = 2L, bookId = "debug-testbuch")
        )
        val verbs = listOf(
            IrregularVerb(id = "i1", infinitive = "(to) fly", simplePast = "flew", germanMeanings = listOf("fliegen"), unit = 1, updatedAt = 3L, bookId = "debug-testbuch")
        )
        // Both books use the same slot, day and badge: they belong to different books.
        val practiceDays = books.map { PracticeDay("2026-10-01", "d1", 2, 2, 10L, it.id) }
        val pointsDays = books.mapIndexed { index, it -> PointsDay("2026-10-01", "d1", 10 * (index + 1), 0, 0, 10L, it.id) }
        val figures = books.mapIndexed { index, it -> Figure(0, "dragon", 0, stage = index + 1, startedAt = 1L, updatedAt = 10L, bookId = it.id) }
        val badges = books.map { Badge("streak", 1, 10L, it.id) }

        val parsed = VocTrainerJson.parseSync(
            VocTrainerJson.encodeSync(books, vocabulary, verbs, practiceDays, pointsDays, figures, badges)
        )

        assertEquals(books, parsed.books)
        assertEquals(vocabulary, parsed.vocabulary)
        assertEquals(verbs, parsed.irregularVerbs)
        assertEquals(practiceDays, parsed.practiceDays)
        assertEquals(pointsDays, parsed.pointsDays)
        assertEquals(figures, parsed.figures)
        assertEquals(badges.toSet(), parsed.badges.toSet())
        assertEquals(QuizFilter(UnitChoice.Number(2), setOf("A")), parsed.books.first().quizFilter(QuizMode.VOCABULARY))
        assertNull(parsed.books.first().quizFilter(QuizMode.VERBS))
    }

    @Test
    fun syncRecordsNeedAKnownBook() {
        val missing = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseSync(
                """{$SYNC, "entries": [{"english": "bag", "german": ["Tasche"], "id": "v1", "leitnerLevel": 2,
                    "updatedAt": 10, "isDeleted": false}]}"""
            )
        }
        assertEquals("Vokabel 1: „bookId“ fehlt", missing.message)
        val unknown = assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseSync(
                """{$SYNC, "practiceDays": [{"date": "2026-10-01", "deviceId": "d1", "roundsCompleted": 2,
                    "dailyGoal": 2, "updatedAt": 100, "bookId": "other"}]}"""
            )
        }
        assertEquals("Übungstag 1: unbekanntes Buch „other“", unknown.message)
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseSync(
                """{"format": "voctrainer-sync", "version": 5, "books": [{"id": "b", "title": "A"}, {"id": "b", "title": "B"}]}"""
            )
        }
    }

    @Test
    fun exactlyOneBookOfASyncFileIsActive() {
        val none = VocTrainerJson.parseSync(
            """{"format": "voctrainer-sync", "version": 5, "books": [{"id": "a", "title": "A"}, {"id": "b", "title": "B"}]}"""
        )
        assertEquals(listOf(true, false), none.books.map { it.isActive })
        val two = VocTrainerJson.parseSync(
            """{"format": "voctrainer-sync", "version": 5, "books": [
                {"id": "a", "title": "A", "isActive": false}, {"id": "b", "title": "B", "isActive": true},
                {"id": "c", "title": "C", "isActive": true}]}"""
        )
        assertEquals(listOf(false, true, false), two.books.map { it.isActive })
        // An unreadable saved selection is dropped, not the file.
        val broken = VocTrainerJson.parseSync(
            """{"format": "voctrainer-sync", "version": 5, "books": [{"id": "a", "title": "A", "quizFilter": "not json"}]}"""
        )
        assertNull(broken.books.single().quizFilter)
    }

    @Test
    fun rejectsSyncWithDuplicateIds() {
        val vocabulary = Vocabulary(id = "same", english = "a", germanMeanings = listOf("a")).inBook()
        val json = encode(listOf(vocabulary, vocabulary.copy(english = "b")), emptyList())
        assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(json) }
    }

    @Test
    fun parsesPageAndTypeFromTheNewExampleFile() {
        val file = VocTrainerJson.parseImport(exampleFile)
        assertEquals(150, file.entries.size)
        assertEquals(36, file.irregularVerbs.size)
        assertTrue(file.entries.all { it.page in 150..163 })
        assertTrue(file.irregularVerbs.all { it.page != null })
        val types = file.entries.groupingBy { VocTrainerJson.wordTypeOf(it.type) }.eachCount()
        assertEquals(mapOf(WordType.NOUN to 58, WordType.VERB to 39, WordType.OTHER to 35, WordType.PHRASE to 18), types)
    }

    @Test
    fun unknownOrMalformedTypeIsTreatedAsMissing() {
        val file = VocTrainerJson.parseImport(
            """{$IMPORT, "entries": [
                {"english": "a", "german": ["a"], "unit": 1, "coreWord": "a", "type": "adjective"},
                {"english": "b", "german": ["b"], "unit": 1, "coreWord": "b", "type": 3},
                {"english": "c", "german": ["c"], "unit": 1, "coreWord": "c", "type": null},
                {"english": "d", "german": ["d"], "unit": 1, "coreWord": "d", "type": "Phrase"}]}"""
        )
        assertEquals(listOf(null, null, null, WordType.PHRASE), file.entries.map { VocTrainerJson.wordTypeOf(it.type) })
    }

    @Test
    fun rejectsInvalidPage() {
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport(
                """{$IMPORT,
                    "entries": [{"english": "a", "german": ["a"], "unit": 1, "page": 0}]}"""
            )
        }
    }

    @Test
    fun rejectsImportFileWithAnOlderVersion() {
        listOf(0, 1).forEach { version ->
            val error = assertThrows(InvalidFileException::class.java) {
                VocTrainerJson.parseImport("""{"format": "voctrainer-import", "version": $version, "entries": []}""")
            }
            assertEquals(VocTrainerJson.OLD_VERSION_MESSAGE, error.message)
        }
    }

    /** Backups below version 5 are not read. */
    @Test
    fun rejectsSyncFileWithAnOlderVersion() {
        listOf(3, 4).forEach { version ->
            val error = assertThrows(InvalidFileException::class.java) {
                VocTrainerJson.parseSync("""{"format": "voctrainer-sync", "version": $version, "entries": []}""")
            }
            assertEquals("Diese Datei stammt aus einer zu alten App-Version und kann nicht gelesen werden.", error.message)
        }
    }

    @Test
    fun rejectsFilesFromANewerAppVersion() {
        val expected = "Die Datei stammt aus einer neueren App-Version. Bitte die App aktualisieren."
        assertEquals(expected, assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseImport("""{"format": "voctrainer-import", "version": 3, "entries": []}""")
        }.message)
        assertEquals(expected, assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseSync("""{"format": "voctrainer-sync", "version": 6, "entries": []}""")
        }.message)
    }

    @Test
    fun parsesCoreWordAndAcceptsButIgnoresMultipleChoiceOnly() {
        val file = VocTrainerJson.parseImport(
            """{$IMPORT, "entries": [
                {"english": "(to) be allowed to", "german": ["dürfen"], "unit": 1, "coreWord": "allowed"},
                {"english": "(to) take sb. for a walk", "german": ["mit jm. spazieren gehen"], "unit": 1, "coreWord": "Walk"},
                {"english": "every Monday/Tuesday/…", "german": ["jeden Montag/Dienstag/…"], "unit": 1,
                 "coreWord": "Monday", "multipleChoiceOnly": true},
                {"english": "Hello!", "german": ["Hallo!"], "unit": 1, "coreWord": "hello", "multipleChoiceOnly": true}]}"""
        )
        assertEquals(listOf("allowed", "Walk", "Monday", "hello"), file.entries.map { it.coreWord })
    }

    @Test
    fun rejectsInvalidCoreWord() {
        listOf("market stall", "stalls", "ma").forEach { coreWord ->
            val error = assertThrows(InvalidFileException::class.java) {
                VocTrainerJson.parseImport(
                    """{$IMPORT, "entries": [
                        {"english": "weather", "german": ["Wetter"], "unit": 1, "coreWord": "weather"},
                        {"english": "market stall", "german": ["Marktstand"], "unit": 1, "coreWord": "$coreWord"}]}"""
                )
            }
            assertEquals(
                "Vokabel 2 („market stall“): „coreWord“ muss ein einzelnes Wort aus dem englischen Eintrag sein",
                error.message
            )
        }
    }

    @Test
    fun englishTokensDropBracketsAndPunctuation() {
        assertEquals(listOf("to", "be", "late"), VocTrainerJson.englishTokens("(to) be late"))
        assertEquals(listOf("Hello", "how", "are", "you"), VocTrainerJson.englishTokens(" Hello,  how are you? "))
    }

    private fun importWithCoreWord(english: String, coreWord: String) = VocTrainerJson.parseImport(
        """{$IMPORT, "entries": [
            {"english": "$english", "german": ["x"], "unit": 1, "coreWord": "$coreWord"}]}"""
    ).entries.single().coreWord

    @Test
    fun coreWordMayKeepTheFinalPeriodOfAnAbbreviation() {
        assertEquals("e.g.", importWithCoreWord("e.g.", "e.g."))
        assertEquals("e.g", importWithCoreWord("e.g.", "e.g"))
        assertEquals("etc.", importWithCoreWord("and so on, etc.", "etc."))
        assertEquals("E.G.", importWithCoreWord("(e.g.) for example", "E.G."))
        assertEquals("didn't", importWithCoreWord("Sam didn't go", "didn't"))
        assertEquals("I'd", importWithCoreWord("I'd like", "I'd"))
        assertEquals("Dad's", importWithCoreWord("Dad's car", "Dad's"))
    }

    @Test
    fun coreWordWithPeriodStillNeedsToBeAWholeWordOfTheEntry() {
        listOf("e.g." to "and so on", "e.g. etc." to "e.g. etc.", "etc." to "e.g.", "go." to "go home").forEach { (coreWord, english) ->
            assertThrows(InvalidFileException::class.java) { importWithCoreWord(english, coreWord) }
        }
    }

    @Test
    fun importReadsExcludedFieldsButSyncNeverWritesThem() {
        val file = VocTrainerJson.parseImport(
            """{$IMPORT, "entries": [
                {"english": "old", "german": ["x"], "unit": 1, "excluded": true, "excludedReason": "veraltet"},
                {"english": "new", "german": ["x"], "unit": 1, "coreWord": "new"}]}"""
        )
        assertEquals(listOf(true, null), file.entries.map { it.excluded })
        assertEquals("veraltet", file.entries.first().excludedReason)

        val json = encode(listOf(Vocabulary(english = "old", germanMeanings = listOf("x")).inBook()), emptyList())
        assertFalse(json.contains("excluded"))
    }

    @Test
    fun syncRoundTripKeepsTheCoreWordAndNoLongerWritesMultipleChoiceOnly() {
        val vocabulary = listOf(
            Vocabulary(id = "v1", english = "market stall", germanMeanings = listOf("Marktstand"), coreWord = "market"),
            Vocabulary(id = "v3", english = "weather", germanMeanings = listOf("Wetter"))
        ).map { it.inBook() }
        val json = encode(vocabulary, emptyList())
        assertTrue(json.contains("\"coreWord\": \"market\""))
        assertFalse(json.contains("multipleChoiceOnly"))
        assertEquals(vocabulary, VocTrainerJson.parseSync(json).vocabulary)
    }

    @Test
    fun multipleChoiceOnlyInABackupIsIgnored() {
        val parsed = VocTrainerJson.parseSync(
            """{$SYNC, "entries": [
                {"english": "every Monday/Tuesday/…", "german": ["x"], "id": "v2", "bookId": "b", "leitnerLevel": 3,
                 "updatedAt": 10, "isDeleted": false, "multipleChoiceOnly": true}]}"""
        ).vocabulary.single()
        assertFalse(parsed.choiceOnly)
        assertEquals(3, parsed.leitnerLevel)
    }

    @Test
    fun syncFileWithoutCoreWordFieldsHasNoCoreWord() {
        val parsed = VocTrainerJson.parseSync(
            """{$SYNC, "entries": [
                {"english": "bag", "german": ["Tasche"], "id": "v1", "bookId": "b", "leitnerLevel": 2, "updatedAt": 10,
                 "isDeleted": false}]}"""
        ).vocabulary.single()
        assertNull(parsed.coreWord)
        assertEquals(false, parsed.choiceOnly)
    }

    @Test
    fun syncFileWithoutHighestLevelUsesTheLevel() {
        val parsed = VocTrainerJson.parseSync(
            """{$SYNC, "entries": [
                {"english": "bag", "german": ["Tasche"], "id": "v1", "bookId": "b", "leitnerLevel": 3, "updatedAt": 10,
                 "isDeleted": false}]}"""
        ).vocabulary.single()
        assertEquals(3, parsed.highestLevel)
    }

    @Test
    fun syncRoundTripKeepsTheHighestLevelAndNeverBelowTheLevel() {
        val vocabulary = listOf(
            Vocabulary(id = "v1", english = "bag", germanMeanings = listOf("Tasche"), leitnerLevel = 1, highestLevel = 5).inBook()
        )
        val json = encode(vocabulary, emptyList())
        assertTrue(json.contains("\"highestLevel\": 5"))
        assertEquals(vocabulary, VocTrainerJson.parseSync(json).vocabulary)

        val below = VocTrainerJson.parseSync(
            """{$SYNC, "entries": [
                {"english": "bag", "german": ["Tasche"], "id": "v1", "bookId": "b", "leitnerLevel": 4, "highestLevel": 2,
                 "updatedAt": 10, "isDeleted": false}]}"""
        ).vocabulary.single()
        assertEquals(4, below.highestLevel)
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseSync(
                """{$SYNC, "entries": [
                    {"english": "bag", "german": ["Tasche"], "id": "v1", "bookId": "b", "leitnerLevel": 4, "highestLevel": 9,
                     "updatedAt": 10, "isDeleted": false}]}"""
            )
        }
    }

    @Test
    fun syncFileWithoutFirstTypedDateReadsAsEmptyAndTheDateRoundTrips() {
        val parsed = VocTrainerJson.parseSync(
            """{$SYNC, "entries": [
                {"english": "bag", "german": ["Tasche"], "id": "v1", "bookId": "b", "leitnerLevel": 3, "updatedAt": 10,
                 "isDeleted": false}]}"""
        ).vocabulary.single()
        assertNull(parsed.firstTypedCorrectAt)

        val vocabulary = listOf(
            Vocabulary(
                id = "v1", english = "bag", germanMeanings = listOf("Tasche"), leitnerLevel = 4, highestLevel = 4, firstTypedCorrectAt = 1234
            ).inBook()
        )
        val json = encode(vocabulary, emptyList())
        assertTrue(json.contains("\"firstTypedCorrectAt\": 1234"))
        assertEquals(vocabulary, VocTrainerJson.parseSync(json).vocabulary)
    }

    @Test
    fun oldBackupWithOnlyFormatVersionIsAnUnknownFormat() {
        val legacy = """{"formatVersion": 1, "entries": []}"""
        assertTrue(
            assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(legacy) }
                .message!!.startsWith("Unbekanntes Dateiformat")
        )
        assertTrue(
            assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseImport(legacy) }
                .message!!.startsWith("Unbekanntes Dateiformat")
        )
    }

    private val fullSettings = SettingsBackup(
        theme = "sunny", vibration = true, autoSpeak = false, editButtonInQuiz = true,
        quizMode = "verbs", dailyGoal = 4, reminderEnabled = false, reminderMinutes = 18 * 60 + 30,
        pointsIntroDone = true, pointsNotice = false, sureLevelIntroDone = true, sureLevelNotice = true,
        levelCapIntroDone = true, levelCapNotice = 0, levelCapCount = 7, tipsDone = listOf("learned_word")
    )

    @Test
    fun backupRoundTripWithSettingsAndAppVariant() {
        val vocabulary = listOf(
            Vocabulary(id = "v1", english = "bag", germanMeanings = listOf("Tasche"), leitnerLevel = 2, highestLevel = 3, updatedAt = 5L).inBook()
        )
        val json = encode(vocabulary, emptyList(), settings = fullSettings, appVariant = VocTrainerJson.APP_VARIANT_DEBUG)

        val parsed = VocTrainerJson.parseSync(json)

        assertEquals(vocabulary, parsed.vocabulary)
        assertEquals(fullSettings, parsed.settings)
        assertEquals("debug", parsed.appVariant)
    }

    @Test
    fun backupRoundTripWithoutSettingsAndAppVariant() {
        val json = encode(emptyList(), emptyList())

        val parsed = VocTrainerJson.parseSync(json)

        assertNull(parsed.settings)
        assertNull(parsed.appVariant)
    }

    @Test
    fun exportWritesTheAppVersion() {
        val json = VocTrainerJson.encodeSync(listOf(book), emptyList(), emptyList(), appVersion = "1.0.0+3 (abc1234)")

        val file = Json.parseToJsonElement(json).jsonObject
        assertEquals("1.0.0+3 (abc1234)", file.getValue("appVersion").jsonPrimitive.content)
        assertEquals(VocTrainerJson.SYNC_VERSION, file.getValue("version").jsonPrimitive.int)
        assertEquals("1.0.0+3 (abc1234)", VocTrainerJson.parseSync(json).appVersion)
    }

    /** Files without "appVersion" are read the same; the version is null. */
    @Test
    fun fileWithoutAppVersionIsReadAsBefore() {
        val vocabulary = listOf(
            Vocabulary(id = "v1", english = "bag", germanMeanings = listOf("Tasche"), leitnerLevel = 4, highestLevel = 5, updatedAt = 5L).inBook()
        )
        val withVersion = VocTrainerJson.encodeSync(
            listOf(book), vocabulary, emptyList(), appVariant = VocTrainerJson.APP_VARIANT_RELEASE, appVersion = "0.8.1"
        )
        val without = JsonObject(Json.parseToJsonElement(withVersion).jsonObject - "appVersion").toString()
        assertFalse(without.contains("appVersion"))

        val parsed = VocTrainerJson.parseSync(without)

        assertNull(parsed.appVersion)
        assertEquals(VocTrainerJson.parseSync(withVersion).copy(appVersion = null), parsed)
    }

    /** A version name this app cannot know (a later release) is information only and never rejects the file. */
    @Test
    fun fileWithUnknownFutureAppVersionIsReadNormally() {
        val vocabulary = listOf(
            Vocabulary(id = "v1", english = "bag", germanMeanings = listOf("Tasche"), leitnerLevel = 2, highestLevel = 3, updatedAt = 5L).inBook()
        )
        val future = JsonObject(
            Json.parseToJsonElement(encode(vocabulary, emptyList())).jsonObject + ("appVersion" to JsonPrimitive("7.3.0-beta+99 (fffffff) dirty"))
        ).toString()

        val parsed = VocTrainerJson.parseSync(future)

        assertEquals("7.3.0-beta+99 (fffffff) dirty", parsed.appVersion)
        assertEquals(vocabulary, parsed.vocabulary)
    }

    @Test
    fun appVariantFollowsTheBuild() {
        assertEquals("debug", VocTrainerJson.appVariant(debug = true))
        assertEquals("release", VocTrainerJson.appVariant(debug = false))
    }

    /** A backup of version 4 (no books) is rejected with the clear message, not misread. */
    @Test
    fun aVersion4BackupIsRejected() {
        val version4 = """
            {
                "format": "voctrainer-sync", "version": 4, "language": null, "source": null, "created": null,
                "entries": [{
                    "english": "bag", "german": ["Tasche"], "unit": 1, "unitTitle": null, "part": "A", "extra": null,
                    "page": 12, "type": "noun", "id": "v1", "leitnerLevel": 3, "updatedAt": 100, "isDeleted": false,
                    "lastReviewedAt": 90, "coreWord": "bag", "multipleChoiceOnly": false, "highestLevel": 4,
                    "firstTypedCorrectAt": 80
                }],
                "practiceDays": [{"date": "2026-10-01", "deviceId": "d1", "roundsCompleted": 2, "dailyGoal": 2, "updatedAt": 100}]
            }
        """.trimIndent()

        val error = assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(version4) }
        assertEquals(VocTrainerJson.OLD_VERSION_MESSAGE, error.message)
    }

    /** Quiz selections in the settings block are ignored (they belong to the book). */
    @Test
    fun invalidSingleSettingValuesAndTheOldQuizSelectionsAreSkipped() {
        val json = """
            {$SYNC, "entries": [], "settings": {
                "theme": "pink", "vibration": "yes", "autoSpeak": false, "quizMode": "grammar",
                "quizFilter": "{\"unit\":\"2\"}", "verbQuizFilter": 3, "dailyGoal": 99, "reminderEnabled": true,
                "reminderMinutes": 1440, "levelCapNotice": -2, "levelCapCount": 4, "unknownSetting": 1
            }}
        """.trimIndent()

        val settings = VocTrainerJson.parseSync(json).settings!!

        assertEquals(SettingsBackup(autoSpeak = false, reminderEnabled = true, levelCapCount = 4), settings)
    }

    @Test
    fun settingsThatAreNoObjectOrEmptyAreIgnored() {
        for (value in listOf("\"x\"", "[]", "null", "{}", """{"theme": 1}""")) {
            val json = """{$SYNC, "entries": [], "settings": $value}"""
            assertNull(value, VocTrainerJson.parseSync(json).settings)
        }
    }

    @Test
    fun syncFileIsVersion5AndHasNoDeviceValuesOrSelectionsInTheSettings() {
        val json = encode(emptyList(), emptyList(), settings = fullSettings)
        assertTrue(json.contains("\"version\": 5"))
        assertFalse(json.contains("device_id"))
        assertFalse(json.contains("\"quizFilter\": \"{"))
    }
}
