package io.github.markushornbach.voctrainer.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * After new vocabulary was imported, the selection card on the start screen pulses once, so the
 * learner notices that the selection ("Auswahl ändern") decides which words are asked.
 * Held in memory only: it is not worth keeping across app restarts.
 */
object SelectionHint {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
