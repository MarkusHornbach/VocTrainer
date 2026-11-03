package io.github.markushornbach.voctrainer.data

data class VocabularyImportResult(
    val vocabularyImported: Int,
    /** Entries of the file that already existed (or came twice in the file); no new entry was created for them. */
    val vocabularySkipped: Int,
    val verbsAdded: Int,
    val verbsUpdated: Int,
    /** Existing entries whose page or word type changed (example book: was filled in). */
    val vocabularyCompleted: Int = 0,
    /** New entries created as ignored because the file marks them as excluded. */
    val vocabularyIgnored: Int = 0,
    /** Existing entries whose core word changed (or whose old "multiple choice only" mark was cleared). */
    val vocabularyClassified: Int = 0,
    /** Existing entries ignored afterwards because they have no core word (see [CoreWords.normalize]). */
    val ignoredWithoutCoreWord: Int = 0,
    /** Existing entries whose content (meanings, unit title, part, page, extra, word type, core word) changed. */
    val vocabularyUpdated: Int = 0,
    /** Active existing entries the file marks as excluded; they are ignored now. */
    val vocabularyExcluded: Int = 0,
    /** Ignored existing entries the file no longer marks as excluded; they are active again. */
    val vocabularyRestored: Int = 0
)

data class ImportPlan(
    val newVocabulary: List<Vocabulary>,
    val skippedVocabulary: Int,
    val newVerbs: List<IrregularVerb>,
    val updatedVerbs: List<IrregularVerb>,
    /** Existing entries that changed in any way. */
    val completedVocabulary: List<Vocabulary> = emptyList(),
    /** How many of [completedVocabulary] got a different page or word type. */
    val filledCount: Int = completedVocabulary.size,
    /** How many of [completedVocabulary] got a different classification. */
    val classifiedCount: Int = 0,
    /** How many of [completedVocabulary] got different content (see [VocabularyImportResult.vocabularyUpdated]). */
    val updatedCount: Int = 0,
    /** How many of [completedVocabulary] were active and are ignored now. */
    val excludedCount: Int = 0,
    /** How many of [completedVocabulary] were ignored and are active now. */
    val restoredCount: Int = 0
) {
    val result: VocabularyImportResult
        get() = VocabularyImportResult(
            newVocabulary.size, skippedVocabulary, newVerbs.size, updatedVerbs.size, filledCount,
            newVocabulary.count { it.isDeleted }, classifiedCount,
            vocabularyUpdated = updatedCount, vocabularyExcluded = excludedCount, vocabularyRestored = restoredCount
        )
}

/**
 * Decides what a textbook import changes, without touching the database.
 * The import file is the reference for existing entries (same English text in the same unit, see
 * [vocabularyKey]): their content and ignore state follow the file; progress and id never change.
 * The example book is the exception (see [plan]).
 */
object VocabularyImporter {
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String = text.trim().lowercase().replace(whitespace, " ")

    fun vocabularyKey(english: String, unit: Int?): Pair<String, Int?> = normalize(english) to unit

    fun verbKey(infinitive: String): String = normalize(infinitive).removePrefix("(to)").trim()

    /**
     * A normal import makes existing entries follow the file (see [followFile]). [exampleBook]: the
     * example book, applied at every start, only fills missing values and never replaces a core
     * word or the ignore state the learner changed (see [completeMissing]).
     */
    fun plan(
        file: VocTrainerFile,
        existingVocabulary: List<Vocabulary>,
        existingVerbs: List<IrregularVerb>,
        now: Long,
        exampleBook: Boolean = false
    ): ImportPlan {
        val existingVocabularyByKey = HashMap<Pair<String, Int?>, Vocabulary>()
        existingVocabulary.sortedBy { it.isDeleted }
            .forEach { existingVocabularyByKey.putIfAbsent(vocabularyKey(it.english, it.unit), it) }
        val knownVocabulary = HashSet(existingVocabularyByKey.keys)
        val seenInFile = HashSet<Pair<String, Int?>>()
        val newVocabulary = mutableListOf<Vocabulary>()
        val completedById = LinkedHashMap<String, Vocabulary>()
        var skipped = 0
        var filled = 0
        var classified = 0
        var updated = 0
        var excluded = 0
        var restored = 0
        for (entry in file.entries) {
            val key = vocabularyKey(entry.english, entry.unit)
            // A normal import takes only the first of several equal entries of the file.
            val repeated = !seenInFile.add(key)
            if (!knownVocabulary.add(key)) {
                skipped++
                if (repeated && !exampleBook) continue
                val existing = existingVocabularyByKey[key]?.let { completedById[it.id] ?: it }
                if (existing != null) {
                    val completed = if (exampleBook) completeMissing(existing, entry, now) else followFile(existing, entry, now)
                    if (completed !== existing) {
                        completedById[existing.id] = completed
                        if (completed.page != existing.page || completed.wordType != existing.wordType) filled++
                        if (completed.coreWord != existing.coreWord || completed.choiceOnly != existing.choiceOnly) classified++
                        if (completed.copy(isDeleted = existing.isDeleted, updatedAt = existing.updatedAt) != existing) updated++
                        if (!existing.isDeleted && completed.isDeleted) excluded++
                        if (existing.isDeleted && !completed.isDeleted) restored++
                    }
                }
                continue
            }
            newVocabulary += Vocabulary(
                english = entry.english,
                germanMeanings = entry.german,
                unit = entry.unit,
                unitTitle = entry.unitTitle.nullIfBlank(),
                part = entry.part.nullIfBlank(),
                extra = entry.extra.nullIfBlank(),
                page = entry.page,
                wordType = VocTrainerJson.wordTypeOf(entry.type),
                updatedAt = now,
                coreWord = entry.coreWord.nullIfBlank()?.trim(),
                isDeleted = entry.excluded == true
            )
        }

        val existingByKey = HashMap<String, IrregularVerb>()
        existingVerbs.forEach { existingByKey.putIfAbsent(verbKey(it.infinitive), it) }
        val updatedById = LinkedHashMap<String, IrregularVerb>()
        val newByKey = LinkedHashMap<String, IrregularVerb>()
        for (verb in file.irregularVerbs) {
            val key = verbKey(verb.infinitive)
            val existing = existingByKey[key]?.let { updatedById[it.id] ?: it }
            val pending = newByKey[key]
            when {
                existing != null -> {
                    if (existing.isDeleted) continue
                    val filled = fillMissing(existing, verb, now)
                    if (filled !== existing) updatedById[existing.id] = filled
                }
                pending != null -> newByKey[key] = fillMissing(pending, verb, now)
                else -> newByKey[key] = IrregularVerb(
                    infinitive = verb.infinitive,
                    simplePast = verb.simplePast,
                    pastParticiple = verb.pastParticiple.nullIfBlank(),
                    germanMeanings = verb.german,
                    unit = verb.unit,
                    part = verb.part.nullIfBlank(),
                    page = verb.page,
                    germanAdded = verb.germanAdded,
                    updatedAt = now
                )
            }
        }
        return ImportPlan(
            newVocabulary, skipped, newByKey.values.toList(), updatedById.values.toList(),
            completedById.values.toList(), filled, classified, updated, excluded, restored
        )
    }

    /**
     * The file is the reference: meanings, unit title, part, page, extra, word type and core word
     * are taken from [source]; a value missing or blank there clears the stored one, except the core
     * word of an excluded entry (it may come without one; then the stored one stays). The entry is
     * ignored if [source] is excluded and active otherwise (an active entry without a usable core
     * word is ignored again afterwards by [CoreWords.normalize]). An old "multiple choice only" mark
     * is cleared. Progress and id are never touched; updatedAt only changes with the content.
     */
    private fun followFile(vocabulary: Vocabulary, source: VocabularyJson, now: Long): Vocabulary {
        val excluded = source.excluded == true
        val fileCoreWord = source.coreWord.nullIfBlank()?.trim()
        val followed = vocabulary.copy(
            germanMeanings = source.german,
            unitTitle = source.unitTitle.nullIfBlank(),
            part = source.part.nullIfBlank(),
            page = source.page,
            extra = source.extra.nullIfBlank(),
            wordType = VocTrainerJson.wordTypeOf(source.type),
            coreWord = if (excluded) fileCoreWord ?: vocabulary.coreWord else fileCoreWord,
            choiceOnly = false,
            isDeleted = excluded
        )
        return if (followed == vocabulary) vocabulary else followed.copy(updatedAt = now)
    }

    /**
     * The example book: fills a missing page / word type of an active entry (never overwriting)
     * and a missing core word. An old "multiple choice only" mark is cleared. Ignored entries are
     * treated the same and stay ignored; progress is never touched.
     */
    private fun completeMissing(vocabulary: Vocabulary, source: VocabularyJson, now: Long): Vocabulary {
        val filled = if (vocabulary.isDeleted) {
            vocabulary
        } else {
            vocabulary.copy(
                page = vocabulary.page ?: source.page,
                wordType = vocabulary.wordType ?: VocTrainerJson.wordTypeOf(source.type)
            )
        }
        val fileCoreWord = source.coreWord.nullIfBlank()?.trim()
        val completed = filled.copy(coreWord = filled.coreWord ?: fileCoreWord, choiceOnly = false)
        return if (completed == vocabulary) vocabulary else completed.copy(updatedAt = now)
    }

    /** Only fills fields that are empty; never overwrites values or touches progress. */
    private fun fillMissing(verb: IrregularVerb, source: IrregularVerbJson, now: Long): IrregularVerb {
        val filled = verb.copy(
            pastParticiple = verb.pastParticiple ?: source.pastParticiple.nullIfBlank(),
            part = verb.part ?: source.part.nullIfBlank(),
            page = verb.page ?: source.page
        )
        return if (filled == verb) verb else filled.copy(updatedAt = now)
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
}
