package io.github.markushornbach.voctrainer.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.markushornbach.voctrainer.BuildConfig
import io.github.markushornbach.voctrainer.ui.theme.VocTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * "Vokabeln editieren", "Vokabel hinzufügen" and the in-quiz edit switch exist only in the test app
 * (debug build); this suite runs against the debug build.
 */
class ManageVocabularyDebugOnlyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun debugBuildShowsEditorEntryAddEntryAndSwitch() {
        var editorOpened = 0
        compose.setContent { VocTrainerTheme { VocabularyScreen(onOpenEditor = { editorOpened++ }) } }
        assertEquals(true, BuildConfig.DEBUG)
        compose.onNodeWithText(DebugMarker.label("Vokabel hinzufügen")).assertIsDisplayed()
        compose.onNodeWithText(DebugMarker.label(EDIT_BUTTON_SETTING_TITLE)).assertIsDisplayed()
        compose.onNodeWithText(DebugMarker.label("Vokabeln editieren")).performClick()
        assertEquals(1, editorOpened)
    }
}
