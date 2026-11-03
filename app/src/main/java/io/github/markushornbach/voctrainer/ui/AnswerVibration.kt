package io.github.markushornbach.voctrainer.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibration after a wrong answer (a correct answer does not vibrate). Played as a media vibration (like in games), not as touch
 * feedback, so it also works when "Touch feedback" is switched off on the phone; it follows
 * the phone's media vibration setting instead.
 */
class AnswerVibration(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Two stronger pulses. */
    fun wrong() = play(
        VibrationEffect.createWaveform(
            longArrayOf(0, WRONG_PULSE_MS, WRONG_PAUSE_MS, WRONG_PULSE_MS),
            intArrayOf(0, 255, 0, 255),
            -1
        )
    )

    private fun play(effect: VibrationEffect) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
        }
    }

    private companion object {
        const val WRONG_PULSE_MS = 70L
        const val WRONG_PAUSE_MS = 90L
    }
}
