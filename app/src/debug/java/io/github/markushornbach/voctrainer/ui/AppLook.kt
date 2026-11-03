package io.github.markushornbach.voctrainer.ui

/**
 * Whether the test app shows its markers (title „VocTrainer Debug“, 🧪 labels, test buttons) on the
 * screens of the manual. Only the manual's screenshot test switches it off, so its images look like
 * the release app; the release build has its own [AppLook] that is always off.
 */
object AppLook {
    @Volatile
    var testMarkers: Boolean = true
}
