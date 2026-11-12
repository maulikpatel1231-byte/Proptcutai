package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

private const val TAG = "StudioSoundEngine"

class StudioSoundEngine {

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val sampleRate = 44100
    private var isPlayingSound = false
    private var volume = 0.8f
    private var isMuted = false

    fun startAmbientTrack(scope: CoroutineScope, mood: String) {
        if (isPlayingSound || isMuted) return
        isPlayingSound = true

        synthJob?.cancel()
        synthJob = scope.launch(Dispatchers.Default) {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(sampleRate / 4)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.setVolume(if (isMuted) 0f else volume * 0.4f)
                audioTrack?.play()

                val buffer = ShortArray(1024)
                var phase = 0.0
                var chordPhase = 0.0

                // Pick harmonious base frequencies depending on mood
                val (baseFreq, chordFreq) = when {
                    mood.contains("Synth", ignoreCase = true) -> Pair(220.0, 330.0) // A3 & E4
                    mood.contains("Chill", ignoreCase = true) || mood.contains("Lofi", ignoreCase = true) -> Pair(261.63, 392.0) // C4 & G4
                    mood.contains("Beat", ignoreCase = true) || mood.contains("Pulse", ignoreCase = true) -> Pair(196.0, 293.66) // G3 & D4
                    else -> Pair(246.94, 370.0) // B3 & F#4
                }

                while (isActive && isPlayingSound) {
                    for (i in buffer.indices) {
                        val sample1 = sin(phase) * 0.6
                        val sample2 = sin(chordPhase) * 0.4
                        val combined = ((sample1 + sample2) * Short.MAX_VALUE * 0.25).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = combined.toShort()

                        phase += 2 * Math.PI * baseFreq / sampleRate
                        chordPhase += 2 * Math.PI * chordFreq / sampleRate
                        if (phase > 2 * Math.PI) phase -= 2 * Math.PI
                        if (chordPhase > 2 * Math.PI) chordPhase -= 2 * Math.PI
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio synthesis failed gracefully", e)
            } finally {
                stopAmbientTrack()
            }
        }
    }

    fun stopAmbientTrack() {
        isPlayingSound = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        audioTrack?.setVolume(if (muted) 0f else volume * 0.4f)
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        if (!isMuted) {
            audioTrack?.setVolume(volume * 0.4f)
        }
    }

    fun playCutChime(scope: CoroutineScope) {
        if (isMuted) return
        scope.launch(Dispatchers.Default) {
            try {
                val chimeTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(22050)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(2048)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                val numSamples = 22050 / 8 // ~120ms short crisp tactile click
                val buf = ShortArray(numSamples)
                var p = 0.0
                for (i in buf.indices) {
                    val envelope = (numSamples - i).toDouble() / numSamples
                    val s = (sin(p) * Short.MAX_VALUE * 0.2 * envelope).toInt()
                    buf[i] = s.toShort()
                    p += 2 * Math.PI * 880.0 / 22050
                }
                chimeTrack.write(buf, 0, buf.size)
                chimeTrack.play()
            } catch (_: Exception) {}
        }
    }
}
