package io.github.markushornbach.voctrainer.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class SpeechState {
    /** The engine is starting; nothing can be spoken yet. */
    CHECKING,
    AVAILABLE,

    /** No speech engine or no English voice; the app speaks nothing and hides the speaker button. */
    UNAVAILABLE
}

/**
 * Offline text-to-speech for English words, one instance for the app. Prefers a British voice
 * (the textbook uses British English), then American, then any English voice. Never throws:
 * without an engine or an English voice it simply reports [SpeechState.UNAVAILABLE].
 */
class Speaker private constructor(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null

    private val _state = MutableStateFlow(SpeechState.CHECKING)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Utterances not finished yet; completed when done, stopped, replaced or failed. */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val nextId = AtomicLong()

    init {
        start()
    }

    private fun start() {
        _state.value = SpeechState.CHECKING
        lateinit var created: TextToSpeech
        // Without any engine the callback can run inside the constructor, so it is handled
        // afterwards on the main thread, when `created` is assigned.
        created = try {
            TextToSpeech(context) { status -> mainHandler.post { onInit(created, status) } }
        } catch (e: RuntimeException) {
            _state.value = SpeechState.UNAVAILABLE
            return
        }
        engine = created
    }

    private fun onInit(created: TextToSpeech, status: Int) {
        if (created !== engine) return
        val ready = status == TextToSpeech.SUCCESS && try {
            configure(created)
        } catch (e: RuntimeException) {
            false
        }
        _state.value = if (ready) SpeechState.AVAILABLE else SpeechState.UNAVAILABLE
    }

    private fun configure(tts: TextToSpeech): Boolean {
        val english = listOf(Locale.UK, Locale.US).any { useLanguage(tts, it) } ||
            tts.availableLanguages.orEmpty().filter { it.language == "en" }.any { useLanguage(tts, it) }
        if (!english) return false
        tts.setSpeechRate(SPEECH_RATE)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        })
        return true
    }

    private fun useLanguage(tts: TextToSpeech, locale: Locale): Boolean {
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        val features = tts.voice?.features.orEmpty()
        return TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features
    }

    private fun finish(utteranceId: String?) {
        utteranceId?.let { pending.remove(it)?.complete(Unit) }
    }

    private fun finishAll() {
        pending.keys.toList().forEach(::finish)
    }

    /**
     * Speaks [text] (converted with [SpeechText.english]), replacing anything still being spoken.
     * Returns a signal that completes when the speech has ended, or null if nothing is spoken.
     */
    fun speak(text: String): CompletableDeferred<Unit>? {
        val tts = engine ?: return null
        if (_state.value != SpeechState.AVAILABLE) return null
        val spoken = SpeechText.english(text)
        if (spoken.isEmpty()) return null
        finishAll()
        val id = "u${nextId.incrementAndGet()}"
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        val result = try {
            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)
        } catch (e: RuntimeException) {
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) {
            finish(id)
            return null
        }
        return done
    }

    fun stop() {
        try {
            engine?.stop()
        } catch (e: RuntimeException) {
            // Nothing to stop.
        }
        finishAll()
    }

    /** Called when the app comes to the foreground: a voice may have been installed meanwhile. */
    fun recheck() {
        if (_state.value != SpeechState.UNAVAILABLE) return
        releaseEngine()
        start()
    }

    private fun releaseEngine() {
        finishAll()
        val old = engine
        engine = null
        try {
            old?.shutdown()
        } catch (e: RuntimeException) {
            // Already gone.
        }
    }

    companion object {
        private const val SPEECH_RATE = 0.9f

        @Volatile
        private var INSTANCE: Speaker? = null

        fun getInstance(context: Context): Speaker =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Speaker(context.applicationContext).also { INSTANCE = it }
            }

        /** Shuts the engine down when the app is closed; the next [getInstance] starts it again. */
        fun release() {
            synchronized(this) {
                INSTANCE?.releaseEngine()
                INSTANCE = null
            }
        }
    }
}
