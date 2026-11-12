package com.aurviz.agent.service.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * High-performance, zero-asset audio synthesizer that generates crisp, modern
 * auditory cues (such as Google Gemini / ChatGPT "tu-dum" listening chimes)
 * using low-latency AudioTrack PCM synthesis.
 */
object SoundEffectPlayer {

    private val scope = CoroutineScope(Dispatchers.Default)
    private const val SAMPLE_RATE = 44100

    /**
     * Plays the rising "tu-dum" wake chime when listening starts.
     * Tone 1: 520 Hz (soft warm low note) for 70ms
     * Tone 2: 880 Hz (crisp rising note) for 110ms with smooth exponential decay.
     */
    fun playStartListeningSound() {
        scope.launch {
            try {
                val buffer1 = generateTone(freqHz = 480.0, durationMs = 70, maxAmplitude = 0.45f)
                val silence = ShortArray((SAMPLE_RATE * 0.02).toInt()) // 20ms micro-gap
                val buffer2 = generateTone(freqHz = 880.0, durationMs = 120, maxAmplitude = 0.65f)

                val fullBuffer = ShortArray(buffer1.size + silence.size + buffer2.size)
                System.arraycopy(buffer1, 0, fullBuffer, 0, buffer1.size)
                System.arraycopy(silence, 0, fullBuffer, buffer1.size, silence.size)
                System.arraycopy(buffer2, 0, fullBuffer, buffer1.size + silence.size, buffer2.size)

                playPcmBuffer(fullBuffer)
            } catch (e: Exception) {
                // Non-critical sound failure
            }
        }
    }

    /**
     * Plays the gentle descending confirmation chime when listening stops.
     * Tone 1: 740 Hz for 60ms
     * Tone 2: 440 Hz for 90ms with smooth fade out.
     */
    fun playStopListeningSound() {
        scope.launch {
            try {
                val buffer1 = generateTone(freqHz = 740.0, durationMs = 60, maxAmplitude = 0.4f)
                val silence = ShortArray((SAMPLE_RATE * 0.015).toInt())
                val buffer2 = generateTone(freqHz = 440.0, durationMs = 100, maxAmplitude = 0.5f)

                val fullBuffer = ShortArray(buffer1.size + silence.size + buffer2.size)
                System.arraycopy(buffer1, 0, fullBuffer, 0, buffer1.size)
                System.arraycopy(silence, 0, fullBuffer, buffer1.size, silence.size)
                System.arraycopy(buffer2, 0, fullBuffer, buffer1.size + silence.size, buffer2.size)

                playPcmBuffer(fullBuffer)
            } catch (e: Exception) {
                // Non-critical sound failure
            }
        }
    }

    private fun generateTone(freqHz: Double, durationMs: Int, maxAmplitude: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        val angularFreq = 2.0 * Math.PI * freqHz

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Apply Hann-like envelope attack and exponential decay to prevent audio pops
            val attackSamples = (SAMPLE_RATE * 0.01).toInt().coerceAtLeast(1)
            val envelope = when {
                i < attackSamples -> i.toFloat() / attackSamples
                else -> {
                    val remaining = numSamples - i
                    (remaining.toFloat() / (numSamples - attackSamples)).coerceIn(0f, 1f)
                }
            }
            val sample = (sin(angularFreq * t) * Short.MAX_VALUE * maxAmplitude * envelope).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        // AudioTrack will clean up after playing static buffer
        scope.launch {
            kotlinx.coroutines.delay(400)
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Ignored
            }
        }
    }
}
