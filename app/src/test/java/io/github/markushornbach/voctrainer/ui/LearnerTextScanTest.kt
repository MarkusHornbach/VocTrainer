package io.github.markushornbach.voctrainer.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Searches every string literal of the app (Kotlin sources and string resources) for texts that were
 * removed on purpose: the second role "Eltern oder Lehrkraft", "forever" statements about points
 * and badges, "Stufen bleiben", the book-choice line "Später änderbar" and the tour ("Rundgang"). Comments are not searched.
 */
class LearnerTextScanTest {
    private val forbidden = listOf(
        Regex("Eltern"), Regex("Lehrkr"), Regex("für immer", RegexOption.IGNORE_CASE),
        Regex("nie verloren", RegexOption.IGNORE_CASE), Regex("Rundgang"),
        Regex("Stufen bleiben"), Regex("Später änderbar")
    )

    /** `src/main` of the app module (unit tests run in the module directory). */
    private val main = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun literals(file: File): List<String> {
        val text = file.readText()
        return if (file.extension == "kt") {
            Regex(""""(?:[^"\\\n]|\\.)*"""").findAll(text).map { it.value }.toList()
        } else {
            Regex(""">([^<]*)<""").findAll(text).map { it.groupValues[1] }.toList()
        }
    }

    @Test
    fun noRemovedTextIsLeftInTheApp() {
        val files = main.walkTopDown().filter {
            it.isFile && (it.extension == "kt" || (it.extension == "xml" && it.parentFile.name.startsWith("values")))
        }.toList()
        assertTrue("sources found", files.any { it.extension == "kt" })
        val hits = files.flatMap { file ->
            literals(file).filter { literal -> forbidden.any { it.containsMatchIn(literal) } }.map { "${file.name}: $it" }
        }
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }
}
