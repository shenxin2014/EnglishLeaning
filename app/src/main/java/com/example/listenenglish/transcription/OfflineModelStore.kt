package com.example.listenenglish.transcription

import android.content.Context
import java.io.File

/** 将 APK 内置的英文 Whisper 模型安装到应用私有目录。 */
object OfflineModelStore {
    private const val ASSET_PATH = "models/ggml-tiny.en.bin"
    private const val MIN_MODEL_BYTES = 70_000_000L

    /** 返回 JNI 可读取的模型文件，首次使用或文件过小时从 APK 内置资源重新复制。 */
    fun tinyEnglish(context: Context): File {
        val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
        val installed = File(modelDirectory, "ggml-tiny.en.bin")
        if (installed.length() >= MIN_MODEL_BYTES) return installed

        context.assets.open(ASSET_PATH).use { input ->
            installed.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        check(installed.length() >= MIN_MODEL_BYTES) { "英文离线转写模型不完整" }
        return installed
    }
}
