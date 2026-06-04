package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class GameAudioSynth {
    private val sampleRate = 22050
    var soundEnabled = true

    // Fire-and-forget synthesizer thread
    private fun playBuffer(buffer: ShortArray) {
        if (!soundEnabled) return
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val bufferSize = buffer.size * 2
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC) // static mode is great for short, pre-rendered clips
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                // Let the sound finish, then release
                val durationMs = (buffer.size.toFloat() / sampleRate * 1000).toLong()
                kotlinx.coroutines.delay(durationMs + 100)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Capture Coin "Pling!" sound
    fun playCoinSfx() {
        val duration = 0.15f // seconds
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Dual frequency tone sweep
            val freq = if (t < duration * 0.4) 987.77 else 1318.51 // B5 followed by E6 (happy major 4th)
            val amplitude = 12000.0 * (1.0 - (t / duration)) // Linear decay
            buffer[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
        }
        playBuffer(buffer)
    }

    // Rescue Success chimes: A celebratory rapid C-E-G-C ascending sparkle
    fun playRescueSfx() {
        val duration = 0.45f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        val noteDur = duration / 4
        val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6 (Ascending Triad)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val noteIdx = (t / noteDur).toInt().coerceAtMost(3)
            val freq = freqs[noteIdx]
            // Calculate progress inside current individual note to decay appropriately
            val noteElapsed = t % noteDur
            val density = 1.0 - (noteElapsed / noteDur)
            val amplitude = 15000.0 * density
            buffer[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
        }
        playBuffer(buffer)
    }

    // Animal calls:
    // Rabbit/Hurt cry (chirpy beep-boop)
    fun playAnimalCrySfx(animalType: Int) {
        val duration = 0.25f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        
        when (animalType) {
            0 -> { // Rabbit chirp (high, vibrating sine)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val modulation = sin(2 * Math.PI * 25 * t) * 50
                    val freq = 1500.0 + modulation
                    val amplitude = 8000.0 * (1f - (t / duration))
                    buffer[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
                }
            }
            1 -> { // Dog whimper/bark: short low bark followed by high yip
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = if (t < duration * 0.3) 250.0 else 400.0 * sin(2 * Math.PI * 10 * t) + 300.0
                    val amplitude = 14000.0 * (1f - (t / duration))
                    buffer[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
                }
            }
            2, 3 -> { // Deer or Fox: Rustling whistling wind
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = 800.0 + sin(2 * Math.PI * 30 * t) * 300.0
                    val amplitude = 10000.0 * (1f - (t / duration))
                    buffer[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
                }
            }
            else -> { // Panda grunt: low frequency pitch sweep
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = 120.0 - (t / duration) * 40.0
                    val amplitude = 16000.0 * (1f - (t / duration))
                    buffer[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
                }
            }
        }
        playBuffer(buffer)
    }

    // UI click sound
    fun playClickSfx() {
        val duration = 0.05f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = 800.0 - (t / duration) * 400.0
            val amplitude = 10000.0 * (1.0 - (t / duration))
            buffer[i] = (amplitude * sin(2 * Math.PI * freq * t)).toInt().toShort()
        }
        playBuffer(buffer)
    }
}
