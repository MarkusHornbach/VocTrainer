package io.github.markushornbach.voctrainer.data

/** The one book of the sync files written by the unit tests. */
internal val testBook = Book("b", "Buch", "en", isActive = true)

/** The head of a sync file of version 5 with [testBook]; its records need `"bookId": "b"`. */
internal const val TEST_SYNC_HEAD = """"format": "voctrainer-sync", "version": 5, "books": [{"id": "b", "title": "Buch", "isActive": true}]"""

/** A sync file of [testBook]; the rows must carry its id (see [inTestBook]). */
internal fun encodeTestSync(
    vocabulary: List<Vocabulary> = emptyList(),
    irregularVerbs: List<IrregularVerb> = emptyList(),
    practiceDays: List<PracticeDay> = emptyList(),
    pointsDays: List<PointsDay> = emptyList(),
    figures: List<Figure> = emptyList(),
    badges: List<Badge> = emptyList()
): String = VocTrainerJson.encodeSync(listOf(testBook), vocabulary, irregularVerbs, practiceDays, pointsDays, figures, badges)

internal fun Badge.inTestBook() = copy(bookId = testBook.id)

internal fun Figure.inTestBook() = copy(bookId = testBook.id)

internal fun PointsDay.inTestBook() = copy(bookId = testBook.id)

internal fun PracticeDay.inTestBook() = copy(bookId = testBook.id)
