package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.markushornbach.voctrainer.data.CoreWords

/**
 * The chosen "Wort zum Tippen" of a form. [tapped] = chosen by the learner: kept while it is still
 * a word of the English text. Otherwise the only word of the text is taken automatically.
 */
data class CoreWordChoice(val word: String? = null, val tapped: Boolean = false) {
    /** After the English text changed to [english]: see the class description. */
    fun afterEnglishChange(english: String): CoreWordChoice {
        if (tapped) CoreWords.candidateFor(word, english)?.let { return CoreWordChoice(it, tapped = true) }
        return CoreWordChoice(CoreWords.derive(english))
    }

    /**
     * The chosen word as written in [english]; a single word is taken automatically. Null if
     * [english] has several words and none of them is chosen.
     */
    fun chosenIn(english: String): String? = CoreWords.candidateFor(word, english) ?: CoreWords.derive(english)

    companion object {
        fun tap(word: String) = CoreWordChoice(word, tapped = true)

        /** Edit page: the stored core word counts as tapped while it is still a word of [english]. */
        fun stored(coreWord: String?, english: String) =
            CoreWordChoice(coreWord, tapped = coreWord != null).afterEnglishChange(english)
    }
}

/** Texts about the "Wort zum Tippen" (forms, restore and import messages). */
object CoreWordTexts {
    const val TITLE = "Wort zum Tippen"
    const val HINT = "Tippe das Wort an, das du später selbst schreibst."
    const val CHOOSE = "Bitte tippe ein Wort an."
    const val NO_WORD = "Bitte gib mindestens ein englisches Wort ein."

    /** Error at the English field: text given, but no word in it (e.g. only "…"). */
    fun englishError(english: String): String? =
        NO_WORD.takeIf { english.isNotBlank() && CoreWords.candidates(english).isEmpty() }

    /** Error of the chip section: there are words, but none is chosen. */
    fun choiceError(english: String, choice: CoreWordChoice): String? =
        CHOOSE.takeIf { CoreWords.candidates(english).isNotEmpty() && choice.chosenIn(english) == null }

    /**
     * Sentence added to a restore or import message; null if nothing was ignored. Only the test
     * app (debug build) has "Vokabeln editieren" where the ignored entries can be found.
     */
    fun ignored(count: Int, pointToEditor: Boolean = BuildConfig.DEBUG): String? {
        if (count <= 0) return null
        val sentence = if (count == 1) "1 Vokabel ohne Wort zum Tippen wurde ignoriert."
        else "$count Vokabeln ohne Wort zum Tippen wurden ignoriert."
        return if (pointToEditor) "$sentence ${DebugMarker.PREFIX}Du findest sie in „Vokabeln editieren“." else sentence
    }

    /** [ignored] with a leading space to append to a message; empty if nothing was ignored. */
    fun ignoredSuffix(count: Int, pointToEditor: Boolean = BuildConfig.DEBUG): String =
        ignored(count, pointToEditor)?.let { " $it" }.orEmpty()
}

object CoreWordTags {
    const val CHIPS = "coreWordChips"
}

/**
 * "Wort zum Tippen": the words of [english] as chips, one can be chosen. The chip row is always at
 * least one chip high, so the form does not jump when the first chips appear while typing.
 */
@Composable
internal fun CoreWordSection(english: String, choice: CoreWordChoice, error: String?, onTap: (String) -> Unit) {
    val candidates = remember(english) { CoreWords.candidates(english) }
    val chosen = remember(english, choice) { choice.chosenIn(english) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(CoreWordTexts.TITLE, style = MaterialTheme.typography.titleSmall)
        Text(
            CoreWordTexts.HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(CoreWordTags.CHIPS),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            candidates.forEach { word ->
                FilterChip(selected = word == chosen, onClick = { onTap(word) }, label = { Text(word) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
