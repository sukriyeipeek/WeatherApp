package com.sukriye.havam.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.sukriye.havam.model.NoiseColor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface AmbientSoundPlayer {
    val playing: StateFlow<NoiseColor?>
    fun play(color: NoiseColor)
    fun stop()
}

// Beyaz/pembe/kahverengi gürültüyü ses dosyası olmadan, gerçek zamanlı üretip çalar
@Singleton
class NoisePlayer @Inject constructor() : AmbientSoundPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _playing = MutableStateFlow<NoiseColor?>(null)
    override val playing: StateFlow<NoiseColor?> = _playing.asStateFlow()

    override fun play(color: NoiseColor) {
        stop()
        _playing.value = color
        job = scope.launch { render(color) }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _playing.value = null
    }

    private suspend fun render(color: NoiseColor) {
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, BUFFER_SIZE * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val generator = NoiseGenerator(color)
        val buffer = FloatArray(BUFFER_SIZE)
        var fade = 0f
        try {
            track.play()
            while (currentCoroutineContext().isActive) {
                for (i in buffer.indices) {
                    // 2 saniyelik yumuşak giriş: ani ses patlaması olmasın
                    fade = minOf(1f, fade + 1f / (SAMPLE_RATE * 2))
                    buffer[i] = generator.next() * VOLUME * fade
                }
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private class NoiseGenerator(private val color: NoiseColor) {
        private val random = Random(System.nanoTime())

        // Pembe gürültü için Paul Kellet filtresinin durumları
        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f
        private var b3 = 0f
        private var b4 = 0f
        private var b5 = 0f
        private var b6 = 0f
        private var brown = 0f

        fun next(): Float {
            val white = random.nextFloat() * 2f - 1f
            return when (color) {
                NoiseColor.White -> white * 0.5f
                NoiseColor.Pink -> {
                    b0 = 0.99886f * b0 + white * 0.0555179f
                    b1 = 0.99332f * b1 + white * 0.0750759f
                    b2 = 0.96900f * b2 + white * 0.1538520f
                    b3 = 0.86650f * b3 + white * 0.3104856f
                    b4 = 0.55000f * b4 + white * 0.5329522f
                    b5 = -0.7616f * b5 - white * 0.0168980f
                    val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
                    b6 = white * 0.115926f
                    pink * 0.11f
                }
                NoiseColor.Brown -> {
                    // Beyaz gürültünün integrali: alçak frekanslar baskın, derin uğultu
                    brown = (brown + 0.02f * white) / 1.02f
                    brown * 3.5f
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BUFFER_SIZE = 2_048
        const val VOLUME = 0.35f
    }
}
