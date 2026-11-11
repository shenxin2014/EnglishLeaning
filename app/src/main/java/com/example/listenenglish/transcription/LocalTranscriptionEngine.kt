package com.example.listenenglish.transcription

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.listenenglish.subtitle.WordTiming

/**
 * 离线转写入口。实现从本地音频生成英文字幕，输出句级时间和可用的词级时间。
 */
interface LocalTranscriptionEngine {
    /** 识别本地英语音频，返回按时间排序的字幕段。 */
    suspend fun transcribe(audioUri: Uri): List<TranscribedSegment>
}

/** 转写输出，含绝对毫秒区间、英文文本和可为空的词级时间串。 */
data class TranscribedSegment(
    val startMs: Long,
    val endMs: Long,
    val englishText: String,
    val wordTimings: String? = null,
)

/** 在设备端准备模型和音频，再通过 JNI 执行英语识别。 */
class WhisperCppTranscriptionEngine(private val context: Context) : LocalTranscriptionEngine {
    /** 解码本地音频、报告识别进度并解析 JNI 返回的字幕段。 */
    suspend fun transcribe(audioUri: Uri, onProgress: (Int) -> Unit): List<TranscribedSegment> {
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "decode started: uri=$audioUri")
        val model = OfflineModelStore.tinyEnglish(context)
        val pcm = PcmAudioDecoder.decode16kMono(context, audioUri)
        check(pcm.isNotEmpty()) { "音频中没有可识别的内容" }
        Log.i(TAG, "decode completed: samples=${pcm.size}, audioSeconds=${pcm.size / 16_000.0}, elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
        val nativeStartedAt = SystemClock.elapsedRealtime()
        return WhisperNativeBridge.transcribe(model.absolutePath, pcm, onProgress)
            ?.mapNotNull(::parseWhisperSegment)
            ?.also { segments ->
                val timedCount = segments.count { it.wordTimings != null }
                Log.i(TAG, "native completed: segments=${segments.size}, wordTimed=$timedCount, elapsedMs=${SystemClock.elapsedRealtime() - nativeStartedAt}")
            }
            ?: error("离线转写引擎执行失败")
    }

    /** 通过同一识别流程执行转写，此入口忽略进度回调。 */
    override suspend fun transcribe(audioUri: Uri): List<TranscribedSegment> = transcribe(audioUri) { }

    /** 集中定义引擎诊断日志标签。 */
    private companion object { const val TAG = "OfflineTranscription" }
}

/** 解析 JNI 的时间头和原文，并用原文的 UTF-8 偏移合并词级时间。 */
internal fun parseWhisperSegment(packed: String): TranscribedSegment? {
    val splitAt = packed.indexOf('\n')
    if (splitAt < 0) return null
    val header = packed.substring(0, splitAt).split('\t', limit = 3)
    if (header.size != 3) return null
    val startMs = header[0].toLongOrNull() ?: return null
    val endMs = header[1].toLongOrNull() ?: return null
    val text = packed.substring(splitAt + 1)
    if (text.isBlank()) return null
    return TranscribedSegment(startMs, endMs, text.trim(), WordTiming.fromWhisper(text, startMs, endMs, header[2]))
}

/** 加载 JNI 库，并串行执行识别以保护当前任务的进度回调。 */
internal object WhisperNativeBridge {
    init { System.loadLibrary("listenwhisper") }
    private val lock = Any()
    private var onProgress: ((Int) -> Unit)? = null
    private var lastProgress = -1

    /** 注册本次识别的进度接收器，执行结束后清除引用。 */
    fun transcribe(modelPath: String, pcm: FloatArray, callback: (Int) -> Unit): Array<String>? = synchronized(lock) {
        onProgress = callback
        lastProgress = -1
        try {
            nativeTranscribe(modelPath, pcm)
        } finally {
            onProgress = null
        }
    }

    /** 接收 JNI 进度，每增长至少两个百分点时通知上层，并始终报告 100%。 */
    @JvmStatic fun onNativeProgress(progress: Int) {
        if (progress >= lastProgress + 2 || progress == 100) {
            lastProgress = progress
            onProgress?.invoke(progress)
        }
    }

    /** 传入模型路径和 16 kHz PCM，返回编码后的字幕段或空值。 */
    private external fun nativeTranscribe(modelPath: String, pcm: FloatArray): Array<String>?
}
