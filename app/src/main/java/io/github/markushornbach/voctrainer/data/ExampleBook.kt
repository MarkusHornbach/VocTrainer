package io.github.markushornbach.voctrainer.data

import android.content.Context

/**
 * The built-in example book "Beispielbuch Englisch": a self-made import file in the app's assets,
 * applied at every start and after a restore (see [VocabularyRepository.ensureExampleBook]).
 */
object ExampleBook {
    const val ID = "beispielbuch"
    const val ASSET = "beispielbuch.json"

    /** Reads the import file from the assets of [context]. */
    fun loader(context: Context): () -> String = {
        context.applicationContext.assets.open(ASSET).use { it.reader(Charsets.UTF_8).readText() }
    }
}
