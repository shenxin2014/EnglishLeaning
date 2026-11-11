package com.example.listenenglish.transcription

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** 把本地音频解码为 Whisper 所需的 16 kHz 单声道浮点 PCM。 */
object PcmAudioDecoder {
    private const val WHISPER_SAMPLE_RATE = 16_000

    /** 选取第一条音轨并解码，将多声道混为单声道后重采样成 16 kHz 浮点 PCM。 */
    fun decode16kMono(context: Context, audioUri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, audioUri, null)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("音频文件中没有可解码的音轨")
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("未知音频格式")
        val codec = MediaCodec.createDecoderByType(mime)
        val samples = FloatCollector()
        try {
            extractor.selectTrack(trackIndex)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: error("无法获取音频输入缓冲区")
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            samples.append(output.slice().order(ByteOrder.LITTLE_ENDIAN), channelCount, pcmEncoding)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            return resample(samples.toArray(), sampleRate)
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
    }

    /** 使用线性插值转换采样率，输入已为 16 kHz 时直接复用。 */
    private fun resample(source: FloatArray, sourceRate: Int): FloatArray {
        if (sourceRate == WHISPER_SAMPLE_RATE) return source
        val outputSize = (source.size.toLong() * WHISPER_SAMPLE_RATE / sourceRate).toInt()
        return FloatArray(outputSize) { outputIndex ->
            val position = outputIndex.toDouble() * sourceRate / WHISPER_SAMPLE_RATE
            val left = position.toInt().coerceIn(0, source.lastIndex)
            val right = min(left + 1, source.lastIndex)
            val fraction = (position - left).toFloat()
            source[left] * (1f - fraction) + source[right] * fraction
        }
    }

    /** 累积解码后的单声道样本，并随音频长度扩展容量。 */
    private class FloatCollector(initialCapacity: Int = WHISPER_SAMPLE_RATE * 30) {
        private var values = FloatArray(initialCapacity)
        private var size = 0

        /** 读取交错 PCM 帧，将各声道样本归一化并求平均。 */
        fun append(buffer: ByteBuffer, channels: Int, encoding: Int) {
            val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
            val frames = buffer.remaining() / (bytesPerSample * channels)
            ensure(size + frames)
            repeat(frames) {
                var mixed = 0f
                repeat(channels) {
                    mixed += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f
                }
                values[size++] = mixed / channels
            }
        }

        /** 返回只包含有效样本的数组副本。 */
        fun toArray() = values.copyOf(size)

        /** 当剩余空间不足时扩展样本缓冲区。 */
        private fun ensure(required: Int) {
            if (required <= values.size) return
            values = values.copyOf(maxOf(required, values.size * 2))
        }
    }
}
