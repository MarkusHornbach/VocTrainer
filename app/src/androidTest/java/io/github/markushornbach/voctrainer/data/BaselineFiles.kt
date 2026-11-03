package io.github.markushornbach.voctrainer.data

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.fail
import java.io.File

/**
 * Golden files of the baseline tests in `androidTest/assets/baseline/`. They are never edited by
 * hand: if a comparison fails, the app changed behaviour. Every run also writes the actual file to
 * the test app's files dir (`baseline/<name>`) and to logcat (tag `Baseline`, lines
 * `<name> <chunk>`), because the test app is uninstalled after a Gradle test run.
 */
object BaselineFiles {
    private val json = Json { prettyPrint = true }

    /** Text of `assets/baseline/[name]` of the test APK, or null if it does not exist. */
    fun asset(name: String): String? = try {
        InstrumentationRegistry.getInstrumentation().context.assets.open("baseline/$name")
            .use { it.reader(Charsets.UTF_8).readText() }
    } catch (e: java.io.IOException) {
        null
    }

    fun writeActual(name: String, text: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "baseline").apply { mkdirs() }
        File(dir, name).writeText(text)
        Log.i(TAG, "$name BEGIN")
        // One line per chunk: the JSON without line breaks.
        Json.parseToJsonElement(text).toString().chunked(LOG_CHUNK).forEach { Log.i(TAG, "$name $it") }
        Log.i(TAG, "$name END")
    }

    fun parse(text: String): JsonObject = json.parseToJsonElement(text) as JsonObject

    /** [file] without the top-level fields [ignored]. */
    fun without(file: JsonObject, vararg ignored: String): JsonObject = JsonObject(file - ignored.toSet())

    /**
     * Replaces the random `id` of every vocabulary entry and verb by a stable key from the book and
     * its texts (the ids are generated at import, so they differ from run to run).
     */
    fun withStableIds(file: JsonObject): JsonObject {
        fun rekey(list: JsonElement?, text: String): JsonElement = JsonArray((list as? JsonArray).orEmpty().map { item ->
            val record = item as JsonObject
            val key = listOf("bookId", text, "unit").joinToString("|") { record[it]?.jsonPrimitive?.content ?: "-" }
            JsonObject(record + ("id" to JsonPrimitive(key)))
        })
        return JsonObject(file + mapOf("entries" to rekey(file["entries"], "english"), "irregularVerbs" to rekey(file["irregularVerbs"], "infinitive")))
    }

    /** Fails with every difference (path, expected, actual) if [actual] is not equal to [expected]. */
    fun assertSame(expected: JsonElement, actual: JsonElement) {
        val differences = ArrayList<String>()
        compare("$", expected, actual, differences)
        if (differences.isNotEmpty()) {
            fail("${differences.size} difference(s):\n" + differences.take(MAX_SHOWN).joinToString("\n"))
        }
    }

    private fun compare(path: String, expected: JsonElement, actual: JsonElement, out: MutableList<String>) {
        when {
            expected is JsonObject && actual is JsonObject -> for (key in (expected.keys + actual.keys).sorted()) {
                val e = expected[key]
                val a = actual[key]
                if (e == null || a == null) out += "$path.$key: expected $e, actual $a" else compare("$path.$key", e, a, out)
            }
            expected is JsonArray && actual is JsonArray -> {
                if (expected.size != actual.size) out += "$path: expected ${expected.size} items, actual ${actual.size}"
                for (i in 0 until minOf(expected.size, actual.size)) compare("$path[$i]", expected[i], actual[i], out)
            }
            expected != actual -> out += "$path: expected $expected, actual $actual"
        }
    }

    private const val MAX_SHOWN = 40
    private const val TAG = "Baseline"
    private const val LOG_CHUNK = 2_000
}
