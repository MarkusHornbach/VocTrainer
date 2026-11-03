package io.github.markushornbach.voctrainer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListedBooksTest {
    private fun book(id: String, title: String, active: Boolean = false) = Book(id, title, "en", isActive = active)

    private val own = book("klasse-6", "Klasse 6", active = true)
    private val example = ExampleBook.ID.let { book(it, "Beispielbuch Englisch") }
    private val other = book("klasse-7", "Klasse 7")

    @Test
    fun releaseHidesAnInactiveExampleBook() {
        assertEquals(listOf("Klasse 6", "Klasse 7"), listedBooks(listOf(own, example, other), false).map { it.second })
    }

    @Test
    fun releaseShowsTheActiveExampleBookInPlaceWithoutSuffix() {
        val active = example.copy(isActive = true)
        assertEquals(
            listOf("Klasse 6", "Beispielbuch Englisch", "Klasse 7"),
            listedBooks(listOf(own.copy(isActive = false), active, other), false).map { it.second }
        )
    }

    @Test
    fun debugAlwaysShowsTheExampleBookLastWithSuffix() {
        assertEquals(
            listOf("Klasse 6", "Klasse 7", "Beispielbuch Englisch (Test)"),
            listedBooks(listOf(example, own, other), true).map { it.second }
        )
        assertEquals(
            listOf("Klasse 6", "Beispielbuch Englisch (Test)"),
            listedBooks(listOf(own.copy(isActive = false), example.copy(isActive = true)), true).map { it.second }
        )
    }
}
