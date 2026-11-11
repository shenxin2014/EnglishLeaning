package com.example.listenenglish.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.listenenglish.ListenEnglishApplication
import com.example.listenenglish.data.CollectionType
import com.example.listenenglish.data.SavedEntryEntity
import com.example.listenenglish.data.SubtitleSegmentEntity
import com.example.listenenglish.subtitle.SrtParser
import com.example.listenenglish.transcription.TranscriptionWorker
import com.example.listenenglish.translation.OfflineWordDictionary
import kotlinx.coroutines.launch

/** 连接界面、Room 仓库与 WorkManager，管理音频导入、查词、收藏和翻译开关。 */
class LearningViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ListenEnglishApplication).repository
    private val preferences = application.getSharedPreferences("learning_preferences", Application.MODE_PRIVATE)
    private val _offlineTranslationEnabled = MutableLiveData(preferences.getBoolean(KEY_OFFLINE_TRANSLATION, false))
    val offlineTranslationEnabled = _offlineTranslationEnabled
    val lessons = repository.lessons
    val savedEntries = repository.savedEntries

    init { resumePendingTranscriptions() }

    /** 观察课程元信息和转写状态。 */
    fun lesson(lessonId: Long) = repository.lessonStream(lessonId)
    /** 观察课程的有序字幕列表。 */
    fun subtitles(lessonId: Long) = repository.subtitleStream(lessonId)

    /** 异步查找离线词条，并将结果传回调用方。 */
    fun lookupWord(word: String, onResult: (OfflineWordDictionary.WordCard) -> Unit) {
        viewModelScope.launch { onResult(repository.lookupWord(word)) }
    }

    /** 保留文件读取权限，导入课程成功后将其加入离线转写队列。 */
    fun importAudio(uri: Uri) = viewModelScope.launch {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            repository.importLesson(uri)
        }.onSuccess { lessonId ->
            enqueueTranscription(lessonId)
        }
    }

    /** 整理用户输入的内容、释义和语境，保存收藏及可选回听起点。 */
    fun saveEntry(
        type: CollectionType,
        expression: String,
        englishDefinition: String,
        chineseMeaning: String,
        lessonId: Long? = null,
        startMs: Long? = null,
        context: String = "",
    ) = viewModelScope.launch {
        repository.addSavedEntry(
            SavedEntryEntity(
                type = type,
                expression = expression.trim(),
                englishDefinition = englishDefinition.trim(),
                chineseMeaning = chineseMeaning.trim(),
                lessonId = lessonId,
                startMs = startMs,
                context = context,
            ),
        )
    }

    /** 解析本地字幕，只有得到有效字幕段时才替换该课程的字幕。 */
    fun importSrt(lessonId: Long, content: String) = viewModelScope.launch {
        val segments = SrtParser.parse(lessonId, content)
        if (segments.isNotEmpty()) repository.replaceSubtitles(lessonId, segments)
    }

    /** 持久化全局开关，打开时为当前课程补充本地中文内容。 */
    fun setOfflineTranslationEnabled(lessonId: Long, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_OFFLINE_TRANSLATION, enabled).apply()
        _offlineTranslationEnabled.value = enabled
        if (enabled) viewModelScope.launch { repository.generateOfflineTranslations(lessonId) }
    }

    /** 启动时查找未完成的课程并重新提交转写请求。 */
    private fun resumePendingTranscriptions() = viewModelScope.launch {
        repository.pendingTranscriptionIds().forEach(::enqueueTranscription)
    }

    /** 把课程标记为排队，并追加到同一条 WorkManager 转写链。 */
    private fun enqueueTranscription(lessonId: Long) {
        viewModelScope.launch {
            repository.markStatus(lessonId, com.example.listenenglish.data.LessonStatus.QUEUED)
            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(Data.Builder().putLong(TranscriptionWorker.KEY_LESSON_ID, lessonId).build())
                .build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "offline_transcription_queue",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }

    /** 定义保存在应用设置中的翻译开关键。 */
    private companion object { const val KEY_OFFLINE_TRANSLATION = "offline_translation_enabled" }
}
