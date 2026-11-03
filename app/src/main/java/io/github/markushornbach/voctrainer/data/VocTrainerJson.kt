package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.settings.QuizFilterCodec
import io.github.markushornbach.voctrainer.settings.SettingsBackup
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

class InvalidFileException(message: String) : Exception(message)

/**
 * Shared file structure for textbook imports ("voctrainer-import") and backups
 * ("voctrainer-sync"; the name is kept so every backup stays readable). Import files only use the textbook fields and name their book ([bookId], [bookTitle]);
 * sync files carry all books ([books]) and on every record its bookId, id, leitnerLevel,
 * highestLevel, updatedAt, isDeleted, lastReviewedAt and firstTypedCorrectAt plus the practice and
 * points days, the figures and the badges, and the optional [settings].
 */
@Serializable
data class VocTrainerFile(
    val format: String,
    val version: Int,
    /** Import only, required: the book the file belongs to (see [Book.id]). */
    val bookId: String? = null,
    /** Import only, required: the display name of the book. */
    val bookTitle: String? = null,
    /** Import: the language of a new book (missing = [Book.DEFAULT_LANGUAGE]). */
    val language: String? = null,
    val source: String? = null,
    val created: String? = null,
    val entries: List<VocabularyJson> = emptyList(),
    val irregularVerbs: List<IrregularVerbJson> = emptyList(),
    /** Sync only: finished rounds per day and device (daily goal and streak). */
    val practiceDays: List<PracticeDayJson> = emptyList(),
    /** Sync only, optional: points per day and device. */
    val pointsDays: List<PointsDayJson> = emptyList(),
    /** Sync only, optional: the learner's figures. */
    val figures: List<FigureJson> = emptyList(),
    /**
     * Sync only, optional: the earned badges (missing = none); series this app version does not
     * know are skipped.
     */
    val badges: List<BadgeJson> = emptyList(),
    /** Sync only, optional: "release" or "debug" (the test app) that wrote the file. Missing = unknown. */
    val appVariant: String? = null,
    /**
     * Sync only, optional: the version name of the app that wrote the file (as shown in the
     * settings, e.g. "1.0.0" or "1.0.0+3 (abc1234)"). Information only; missing = unknown.
     */
    val appVersion: String? = null,
    /**
     * Sync only, optional: the learner's settings (see [SettingsBackup]); read leniently, so an
     * invalid value never rejects the file.
     */
    val settings: JsonElement? = null,
    /** Sync only, required: every book of the device except the example book. */
    val books: List<BookJson> = emptyList()
)

@Serializable
data class BookJson(
    val id: String,
    val title: String,
    val language: String = Book.DEFAULT_LANGUAGE,
    val isActive: Boolean = false,
    /** The saved vocabulary selection (see [io.github.markushornbach.voctrainer.settings.QuizFilterCodec]); an invalid one is dropped. */
    val quizFilter: String? = null,
    val verbQuizFilter: String? = null,
    /** [Book.figureSlotStart]; optional, missing = 0. */
    val figureSlotStart: Int = 0
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VocabularyJson(
    val english: String,
    val german: List<String>,
    val unit: Int? = null,
    val unitTitle: String? = null,
    val part: String? = null,
    val extra: String? = null,
    val page: Int? = null,
    /** "verb", "noun", "phrase" or "other"; any other value counts as missing. */
    val type: JsonElement? = null,
    val id: String? = null,
    val leitnerLevel: Int? = null,
    val updatedAt: Long? = null,
    val isDeleted: Boolean? = null,
    val lastReviewedAt: Long? = null,
    /**
     * The one word of [english] a learner types (see [Vocabulary.coreWord]). Required in import
     * files for every entry that is not [excluded]; optional in sync files. A field
     * "multipleChoiceOnly" is ignored as unknown.
     */
    val coreWord: String? = null,
    /**
     * Import only, optional: an entry marked `true` is ignored (a new one is created ignored, an
     * existing one becomes ignored); absent or `false` makes an ignored existing entry active again
     * (not for the example book, see [VocabularyImporter.plan]). Never written to backups.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val excluded: Boolean? = null,
    /** Import only, optional: why the entry is [excluded]; informational, not stored. Never written to backups. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val excludedReason: String? = null,
    /** Sync only, optional: highest level ever reached; missing = [leitnerLevel]. */
    val highestLevel: Int? = null,
    /** Sync only, optional: when the word was first typed correctly (epoch millis). Missing = never. */
    val firstTypedCorrectAt: Long? = null,
    /** Sync only, required: [Book.id] of the entry. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bookId: String? = null
)

@Serializable
data class IrregularVerbJson(
    val infinitive: String,
    val simplePast: String,
    val pastParticiple: String? = null,
    val german: List<String>,
    val unit: Int,
    val part: String? = null,
    val page: Int? = null,
    val germanAdded: Boolean = false,
    val id: String? = null,
    val leitnerLevel: Int? = null,
    val updatedAt: Long? = null,
    val isDeleted: Boolean? = null,
    val lastReviewedAt: Long? = null,
    /** Sync only, required: [Book.id] of the verb. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bookId: String? = null
)

// The records below exist only in sync files; their bookId is required there.

@Serializable
data class PracticeDayJson(
    val date: String,
    val deviceId: String,
    val roundsCompleted: Int,
    val dailyGoal: Int,
    val updatedAt: Long,
    val bookId: String? = null
)

@Serializable
data class PointsDayJson(
    val date: String,
    val deviceId: String,
    val risePoints: Int = 0,
    val dailyBonus: Int = 0,
    val startPoints: Int = 0,
    val updatedAt: Long,
    val bookId: String? = null
)

@Serializable
data class FigureJson(
    val slot: Int,
    val kind: String,
    val pointsAtStart: Int,
    val stage: Int,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val updatedAt: Long,
    val bookId: String? = null
)

@Serializable
data class BadgeJson(
    /** [io.github.markushornbach.voctrainer.learning.BadgeSeries.key]. */
    val series: String,
    /** 1..4. */
    val tier: Int,
    val reachedAt: Long,
    val bookId: String? = null
)

data class SyncData(
    val vocabulary: List<Vocabulary>,
    val irregularVerbs: List<IrregularVerb>,
    val practiceDays: List<PracticeDay> = emptyList(),
    val pointsDays: List<PointsDay> = emptyList(),
    val figures: List<Figure> = emptyList(),
    /** Badges of known series only. */
    val badges: List<Badge> = emptyList(),
    /** Only the valid settings of the file; null if the file has none. */
    val settings: SettingsBackup? = null,
    val appVariant: String? = null,
    /** Version name of the app that wrote the file, null if missing (see [VocTrainerFile.appVersion]). */
    val appVersion: String? = null,
    /** All books of the file, exactly one active (see [withOneActive]) if there is any. */
    val books: List<Book> = emptyList()
)

object VocTrainerJson {
    const val IMPORT_FORMAT = "voctrainer-import"
    /** The only import version read; older ones are rejected ([OLD_VERSION_MESSAGE]). */
    const val IMPORT_VERSION = 2
    const val SYNC_FORMAT = "voctrainer-sync"
    /** The only backup version read; older ones are rejected ([OLD_VERSION_MESSAGE]). */
    const val SYNC_VERSION = 5

    const val NO_BOOK_MESSAGE =
        "Diese Datei gehört zu keinem Buch (Buch-Kennung fehlt). Bitte eine aktuelle Vokabeldatei verwenden."

    const val NO_BOOK_TITLE_MESSAGE =
        "Der Name des Buchs (bookTitle) fehlt. Bitte eine aktuelle Vokabeldatei verwenden."

    const val OLD_VERSION_MESSAGE = "Diese Datei stammt aus einer zu alten App-Version und kann nicht gelesen werden."
    const val MAX_ENTRIES = 10_000
    const val APP_VARIANT_RELEASE = "release"
    const val APP_VARIANT_DEBUG = "debug"

    fun appVariant(debug: Boolean): String = if (debug) APP_VARIANT_DEBUG else APP_VARIANT_RELEASE

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Reads and checks an import file. In the result [VocTrainerFile.bookId] and
     * [VocTrainerFile.bookTitle] are set (trimmed) and [VocTrainerFile.language] has its default.
     */
    fun parseImport(text: String): VocTrainerFile {
        val root = parseRoot(text)
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format == SYNC_FORMAT) {
            throw invalid(
                "Das ist eine Sicherungsdatei. Bitte „Sicherung wiederherstellen“ verwenden."
            )
        }
        if (format != IMPORT_FORMAT) {
            throw invalid("Unbekanntes Dateiformat – erwartet wird „$IMPORT_FORMAT“.")
        }
        requireVersion(root, IMPORT_VERSION)

        val file = decode<VocTrainerFile>(root)
        val bookId = file.bookId?.trim()?.takeIf { it.isNotEmpty() } ?: throw invalid(NO_BOOK_MESSAGE)
        val bookTitle = file.bookTitle?.trim()?.takeIf { it.isNotEmpty() } ?: throw invalid(NO_BOOK_TITLE_MESSAGE)
        checkCount(file)
        file.entries.forEachIndexed { index, entry ->
            val label = "Vokabel ${index + 1}"
            requireText(entry.english, "$label: „english“ fehlt")
            requireMeanings(entry.german, "$label („${entry.english}“)")
            if (entry.unit == null) throw invalid("$label („${entry.english}“): „unit“ fehlt")
            requirePage(entry.page, "$label („${entry.english}“)")
            requireCoreWord(entry.coreWord, entry.english, "$label („${entry.english}“)")
        }
        requireCoreWords(file.entries)
        file.irregularVerbs.forEachIndexed { index, verb ->
            val label = "Unregelmäßiges Verb ${index + 1}"
            requireText(verb.infinitive, "$label: „infinitive“ fehlt")
            requireText(verb.simplePast, "$label („${verb.infinitive}“): „simplePast“ fehlt")
            requireMeanings(verb.german, "$label („${verb.infinitive}“)")
            requirePage(verb.page, "$label („${verb.infinitive}“)")
        }
        val language = file.language?.trim()?.takeIf { it.isNotEmpty() } ?: Book.DEFAULT_LANGUAGE
        return file.copy(bookId = bookId, bookTitle = bookTitle, language = language)
    }

    /**
     * [allowNegativeRisePoints]: only the test app, whose "Punkte setzen" may store negative rise
     * points; it can then read back its own backups. The release app keeps rejecting them.
     */
    fun parseSync(text: String, allowNegativeRisePoints: Boolean = false): SyncData {
        val root = parseRoot(text)
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format == IMPORT_FORMAT) {
            throw invalid("Das ist eine Vokabel-Importdatei. Bitte „Vokabeln importieren“ verwenden.")
        }
        if (format != SYNC_FORMAT) throw invalid("Unbekanntes Dateiformat – erwartet wird eine Sicherungsdatei.")
        requireVersion(root, SYNC_VERSION)

        val file = decode<VocTrainerFile>(root)
        checkCount(file)
        val bookIds = HashSet<String>()
        val books = file.books.mapIndexed { index, book ->
            val label = "Buch ${index + 1}"
            requireText(book.id, "$label: „id“ fehlt")
            requireText(book.title, "$label: „title“ fehlt")
            requireText(book.language, "$label: „language“ fehlt")
            if (!bookIds.add(book.id)) throw invalid("$label: doppelte id ${book.id}")
            if (book.figureSlotStart < 0) throw invalid("$label: „figureSlotStart“ ist ungültig")
            Book(
                id = book.id,
                title = book.title,
                language = book.language,
                isActive = book.isActive,
                quizFilter = book.quizFilter?.takeIf { QuizFilterCodec.decode(it) != null },
                verbQuizFilter = book.verbQuizFilter?.takeIf { QuizFilterCodec.decode(it) != null },
                figureSlotStart = book.figureSlotStart
            )
        }.withOneActive()
        fun book(id: String?, label: String): String {
            if (id.isNullOrBlank()) throw invalid("$label: „bookId“ fehlt")
            if (id !in bookIds) throw invalid("$label: unbekanntes Buch „$id“")
            return id
        }
        val vocabularyIds = HashSet<String>()
        val vocabulary = file.entries.mapIndexed { index, entry ->
            val label = "Vokabel ${index + 1}"
            requireText(entry.english, "$label: „english“ fehlt")
            requireMeanings(entry.german, "$label („${entry.english}“)")
            val level = requireLevel(entry.leitnerLevel, label)
            val highestLevel = entry.highestLevel
            if (highestLevel != null && highestLevel !in 0..Vocabulary.MAX_LEITNER_LEVEL) {
                throw invalid("$label: „highestLevel“ muss zwischen 0 und ${Vocabulary.MAX_LEITNER_LEVEL} liegen")
            }
            Vocabulary(
                id = requireSyncId(entry.id, vocabularyIds, label),
                english = entry.english,
                germanMeanings = entry.german,
                unit = entry.unit,
                unitTitle = entry.unitTitle,
                part = entry.part,
                extra = entry.extra,
                page = entry.page.also { requirePage(it, label) },
                wordType = wordTypeOf(entry.type),
                leitnerLevel = level,
                updatedAt = requireTimestamp(entry.updatedAt, label),
                isDeleted = entry.isDeleted ?: throw invalid("$label: „isDeleted“ fehlt"),
                lastReviewedAt = entry.lastReviewedAt.also { requireReviewDate(it, label) },
                coreWord = entry.coreWord?.trim()?.takeIf { it.isNotEmpty() },
                highestLevel = maxOf(highestLevel ?: level, level),
                firstTypedCorrectAt = entry.firstTypedCorrectAt.also {
                    if (it != null && it < 0) throw invalid("$label: „firstTypedCorrectAt“ ist ungültig")
                },
                bookId = book(entry.bookId, label)
            )
        }
        val verbIds = HashSet<String>()
        val verbs = file.irregularVerbs.mapIndexed { index, verb ->
            val label = "Unregelmäßiges Verb ${index + 1}"
            requireText(verb.infinitive, "$label: „infinitive“ fehlt")
            requireText(verb.simplePast, "$label: „simplePast“ fehlt")
            requireMeanings(verb.german, "$label („${verb.infinitive}“)")
            IrregularVerb(
                id = requireSyncId(verb.id, verbIds, label),
                infinitive = verb.infinitive,
                simplePast = verb.simplePast,
                pastParticiple = verb.pastParticiple,
                germanMeanings = verb.german,
                unit = verb.unit,
                part = verb.part,
                page = verb.page.also { requirePage(it, label) },
                germanAdded = verb.germanAdded,
                leitnerLevel = requireLevel(verb.leitnerLevel, label),
                updatedAt = requireTimestamp(verb.updatedAt, label),
                isDeleted = verb.isDeleted ?: throw invalid("$label: „isDeleted“ fehlt"),
                lastReviewedAt = verb.lastReviewedAt.also { requireReviewDate(it, label) },
                bookId = book(verb.bookId, label)
            )
        }
        val dayKeys = HashSet<Triple<String, String, String>>()
        val practiceDays = file.practiceDays.mapIndexed { index, day ->
            val label = "Übungstag ${index + 1}"
            val bookId = book(day.bookId, label)
            requireDate(day.date, label)
            requireText(day.deviceId, "$label: „deviceId“ fehlt")
            if (!dayKeys.add(Triple(bookId, day.date, day.deviceId))) throw invalid("$label: doppelter Eintrag ${day.date}")
            if (day.roundsCompleted < 0) throw invalid("$label: „roundsCompleted“ ist ungültig")
            if (day.dailyGoal < 1) throw invalid("$label: „dailyGoal“ ist ungültig")
            PracticeDay(day.date, day.deviceId, day.roundsCompleted, day.dailyGoal, requireTimestamp(day.updatedAt, label), bookId)
        }
        val pointsKeys = HashSet<Triple<String, String, String>>()
        val pointsDays = file.pointsDays.mapIndexed { index, day ->
            val label = "Punkte-Tag ${index + 1}"
            val bookId = book(day.bookId, label)
            requireDate(day.date, label)
            requireText(day.deviceId, "$label: „deviceId“ fehlt")
            if (!pointsKeys.add(Triple(bookId, day.date, day.deviceId))) throw invalid("$label: doppelter Eintrag ${day.date}")
            if ((day.risePoints < 0 && !allowNegativeRisePoints) || day.dailyBonus < 0 || day.startPoints < 0) {
                throw invalid("$label: Punkte dürfen nicht negativ sein")
            }
            PointsDay(
                day.date, day.deviceId, day.risePoints, day.dailyBonus, day.startPoints,
                requireTimestamp(day.updatedAt, label), bookId
            )
        }
        val figureSlots = HashSet<Pair<String, Int>>()
        // Kinds this app version does not know (added later) are skipped, not rejected.
        val figures = file.figures.mapIndexedNotNull { index, figure ->
            val label = "Avatar ${index + 1}"
            val bookId = book(figure.bookId, label)
            if (figure.slot < 0 || !figureSlots.add(bookId to figure.slot)) throw invalid("$label: „slot“ ist ungültig")
            if (figure.stage !in 0..FigureRules.MAX_STAGE) throw invalid("$label: „stage“ ist ungültig")
            if (figure.pointsAtStart < 0) throw invalid("$label: „pointsAtStart“ ist ungültig")
            requireTimestamp(figure.startedAt, label)
            figure.finishedAt?.let { requireTimestamp(it, label) }
            requireTimestamp(figure.updatedAt, label)
            if (FigureKind.fromKey(figure.kind) == null) return@mapIndexedNotNull null
            Figure(
                figure.slot, figure.kind, figure.pointsAtStart, figure.stage, figure.startedAt,
                figure.finishedAt, figure.updatedAt, bookId
            )
        }
        val badgeKeys = HashSet<Triple<String, String, Int>>()
        // Series this app version does not know (added later) are skipped, not rejected.
        val badges = file.badges.mapIndexedNotNull { index, badge ->
            val label = "Abzeichen ${index + 1}"
            val bookId = book(badge.bookId, label)
            if (BadgeSeries.fromKey(badge.series) == null) return@mapIndexedNotNull null
            if (badge.tier !in 1..BadgeRules.MAX_TIER) throw invalid("$label: „tier“ muss zwischen 1 und ${BadgeRules.MAX_TIER} liegen")
            if (badge.reachedAt < 0) throw invalid("$label: „reachedAt“ ist ungültig")
            if (!badgeKeys.add(Triple(bookId, badge.series, badge.tier))) {
                throw invalid("$label: doppelter Eintrag ${badge.series} ${badge.tier}")
            }
            Badge(badge.series, badge.tier, badge.reachedAt, bookId)
        }
        return SyncData(
            vocabulary, verbs, practiceDays, pointsDays, figures, badges,
            settings = SettingsBackup.fromJson(file.settings)?.takeUnless { it.isEmpty },
            appVariant = file.appVariant,
            appVersion = file.appVersion,
            books = books
        )
    }

    fun encodeSync(
        books: List<Book>,
        vocabulary: List<Vocabulary>,
        irregularVerbs: List<IrregularVerb>,
        practiceDays: List<PracticeDay> = emptyList(),
        pointsDays: List<PointsDay> = emptyList(),
        figures: List<Figure> = emptyList(),
        badges: List<Badge> = emptyList(),
        settings: SettingsBackup? = null,
        appVariant: String? = null,
        appVersion: String? = null
    ): String =
        json.encodeToString(
            VocTrainerFile(
                format = SYNC_FORMAT,
                version = SYNC_VERSION,
                books = books.map { BookJson(it.id, it.title, it.language, it.isActive, it.quizFilter, it.verbQuizFilter, it.figureSlotStart) },
                entries = vocabulary.map {
                    VocabularyJson(
                        english = it.english,
                        german = it.germanMeanings,
                        unit = it.unit,
                        unitTitle = it.unitTitle,
                        part = it.part,
                        extra = it.extra,
                        page = it.page,
                        type = it.wordType?.let { type -> JsonPrimitive(type.jsonValue) },
                        id = it.id,
                        leitnerLevel = it.leitnerLevel,
                        updatedAt = it.updatedAt,
                        isDeleted = it.isDeleted,
                        lastReviewedAt = it.lastReviewedAt,
                        coreWord = it.coreWord,
                        highestLevel = it.highestLevel,
                        firstTypedCorrectAt = it.firstTypedCorrectAt,
                        bookId = it.bookId
                    )
                },
                irregularVerbs = irregularVerbs.map {
                    IrregularVerbJson(
                        infinitive = it.infinitive,
                        simplePast = it.simplePast,
                        pastParticiple = it.pastParticiple,
                        german = it.germanMeanings,
                        unit = it.unit,
                        part = it.part,
                        page = it.page,
                        germanAdded = it.germanAdded,
                        id = it.id,
                        leitnerLevel = it.leitnerLevel,
                        updatedAt = it.updatedAt,
                        isDeleted = it.isDeleted,
                        lastReviewedAt = it.lastReviewedAt,
                        bookId = it.bookId
                    )
                },
                practiceDays = practiceDays.map {
                    PracticeDayJson(it.date, it.deviceId, it.roundsCompleted, it.dailyGoal, it.updatedAt, it.bookId)
                },
                pointsDays = pointsDays.map {
                    PointsDayJson(it.date, it.deviceId, it.risePoints, it.dailyBonus, it.startPoints, it.updatedAt, it.bookId)
                },
                figures = figures.map {
                    FigureJson(it.slot, it.kind, it.pointsAtStart, it.stage, it.startedAt, it.finishedAt, it.updatedAt, it.bookId)
                },
                badges = badges.filter { BadgeSeries.fromKey(it.series) != null }
                    .sortedWith(compareBy({ it.bookId }, { it.series }, { it.tier })).map {
                    BadgeJson(it.series, it.tier, it.reachedAt, it.bookId)
                },
                appVariant = appVariant,
                appVersion = appVersion,
                settings = settings?.toJson()
            )
        )

    private fun parseRoot(text: String): JsonObject {
        val element: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw invalid("Die Datei ist keine gültige JSON-Datei.")
        }
        return element as? JsonObject ?: throw invalid("Die Datei ist keine gültige JSON-Datei.")
    }

    private inline fun <reified T> decode(root: JsonObject): T = try {
        json.decodeFromJsonElement<T>(root)
    } catch (e: SerializationException) {
        throw invalid("Ungültiger Aufbau der Datei: ${e.message}")
    } catch (e: IllegalArgumentException) {
        throw invalid("Ungültiger Aufbau der Datei: ${e.message}")
    }

    /**
     * Accepts the current version of a format. A higher version comes from a newer app and asks
     * for an update; a lower one is rejected with [OLD_VERSION_MESSAGE].
     */
    private fun requireVersion(root: JsonObject, expected: Int) {
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
            ?: throw invalid("Die Versionsangabe fehlt.")
        if (version > expected) throw invalid(NEWER_VERSION_MESSAGE)
        if (version != expected) throw invalid(OLD_VERSION_MESSAGE)
    }

    private const val NEWER_VERSION_MESSAGE =
        "Die Datei stammt aus einer neueren App-Version. Bitte die App aktualisieren."

    private val whitespace = Regex("\\s+")
    private val coreWordTrim = charArrayOf('(', ')', ',', '.', ';', ':', '!', '?')

    /** Like [coreWordTrim] but keeps a final period, so abbreviations such as "e.g." stay whole. */
    private val coreWordTrimKeepingPeriod = charArrayOf('(', ')', ',', ';', ':', '!', '?')

    /** The single words of an English entry, without surrounding brackets and punctuation. */
    fun englishTokens(english: String): List<String> =
        english.split(whitespace).map { it.trim(*coreWordTrim) }.filter { it.isNotEmpty() }

    /** The single words of an entry without brackets and punctuation, but with their periods ("e.g."). */
    private fun tokensKeepingPeriod(text: String): List<String> =
        text.split(whitespace).map { it.trim(*coreWordTrimKeepingPeriod) }.filter { it.isNotEmpty() }

    /**
     * A core word must be one word that occurs as a whole word in the entry (a final period may be
     * part of it); every [CoreWords.candidates] word is one. A missing one is checked by [requireCoreWords].
     */
    private fun requireCoreWord(coreWord: String?, text: String, label: String) {
        if (coreWord.isNullOrBlank()) return
        if (!isValidCoreWord(coreWord, text)) {
            throw invalid("$label: „coreWord“ muss ein einzelnes Wort aus dem englischen Eintrag sein")
        }
    }

    /** The import check of a given core word (see [requireCoreWord]). */
    internal fun isValidCoreWord(coreWord: String, text: String): Boolean =
        coreWord.isNotBlank() && coreWord.none { it.isWhitespace() } &&
            ((englishTokens(text) + tokensKeepingPeriod(text)).any { it.equals(coreWord, ignoreCase = true) } ||
                CoreWords.candidateFor(coreWord, text) != null)

    /** Every vocabulary entry of an import file that is not excluded needs a core word; else nothing is imported. */
    private fun requireCoreWords(entries: List<VocabularyJson>) {
        val missing = entries.filter { it.excluded != true && it.coreWord.isNullOrBlank() }
        if (missing.isEmpty()) return
        val shown = missing.take(MAX_LISTED_MISSING).joinToString(", ") { "„${it.english}“" }
        val more = missing.size - MAX_LISTED_MISSING
        val count = if (missing.size == 1) "1 Vokabel" else "${missing.size} Vokabeln"
        throw invalid(
            "In der Datei fehlt bei $count das Wort zum Tippen (coreWord): $shown" +
                (if (more > 0) " und $more weitere" else "") +
                ". Bitte die Importdatei neu erstellen."
        )
    }

    private const val MAX_LISTED_MISSING = 5

    private fun checkCount(file: VocTrainerFile) {
        if (file.entries.size + file.irregularVerbs.size > MAX_ENTRIES) {
            throw invalid("Zu viele Einträge (max. $MAX_ENTRIES).")
        }
    }

    private fun requireText(value: String, message: String) {
        if (value.isBlank()) throw invalid(message)
    }

    private fun requireMeanings(meanings: List<String>, label: String) {
        if (meanings.isEmpty() || meanings.any { it.isBlank() }) {
            throw invalid("$label: Die deutsche Bedeutung fehlt oder ist leer.")
        }
    }

    fun wordTypeOf(value: JsonElement?): WordType? =
        WordType.fromJsonValue((value as? JsonPrimitive)?.takeIf { it.isString }?.content)

    private fun requirePage(page: Int?, label: String) {
        if (page != null && page < 1) throw invalid("$label: „page“ muss eine positive Zahl sein")
    }

    private fun requireSyncId(id: String?, seen: MutableSet<String>, label: String): String {
        if (id.isNullOrBlank()) throw invalid("$label: „id“ fehlt")
        if (!seen.add(id)) throw invalid("$label: doppelte id $id")
        return id
    }

    private fun requireLevel(level: Int?, label: String): Int {
        if (level == null || level !in 0..Vocabulary.MAX_LEITNER_LEVEL) {
            throw invalid("$label: „leitnerLevel“ muss zwischen 0 und ${Vocabulary.MAX_LEITNER_LEVEL} liegen")
        }
        return level
    }

    private fun requireDate(date: String, label: String) {
        try {
            LocalDate.parse(date)
        } catch (e: DateTimeParseException) {
            throw invalid("$label: „date“ ist kein gültiges Datum")
        }
    }

    private fun requireReviewDate(lastReviewedAt: Long?, label: String) {
        if (lastReviewedAt != null && lastReviewedAt < 0) throw invalid("$label: „lastReviewedAt“ ist ungültig")
    }

    private fun requireTimestamp(updatedAt: Long?, label: String): Long {
        if (updatedAt == null || updatedAt < 0) throw invalid("$label: „updatedAt“ fehlt oder ist ungültig")
        return updatedAt
    }

    private fun invalid(message: String) = InvalidFileException(message)
}
