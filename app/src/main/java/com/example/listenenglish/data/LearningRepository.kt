package com.example.listenenglish.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.listenenglish.translation.OfflineEnglishChineseTranslator
import com.example.listenenglish.translation.OfflineWordDictionary

/** 为 ViewModel 和转写任务统一提供课程、字幕、收藏和离线词典数据。 */
class LearningRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    val lessons = database.lessonDao().observeAll()
    val savedEntries = database.savedEntryDao().observeAll()

    /** 提供单课程的可观察记录。 */
    fun lessonStream(lessonId: Long) = database.lessonDao().observeById(lessonId)
    /** 提供按时间排序的课程字幕及其更新。 */
    fun subtitleStream(lessonId: Long) = database.subtitleDao().observeByLesson(lessonId)

    /** 从音频 URI 读取标题、作者和时长，保存新课程并返回其 ID。 */
    suspend fun importLesson(uri: Uri): Long = withContext(Dispatchers.IO) {
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(context, uri)
            val title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: "未命名音频"
            val artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            database.lessonDao().insert(
                LessonEntity(sourceUri = uri.toString(), title = title, artist = artist, durationMs = duration),
            )
        } finally {
            metadata.release()
        }
    }

    /** 在 IO 调度器上保存词、短语或句子收藏。 */
    suspend fun addSavedEntry(entry: SavedEntryEntity) = withContext(Dispatchers.IO) {
        database.savedEntryDao().insert(entry)
    }

    /** 先用规范词形查询 Room，未命中时使用内置词典及未收录提示。 */
    suspend fun lookupWord(rawWord: String): OfflineWordDictionary.WordCard = withContext(Dispatchers.IO) {
        val lemma = OfflineWordDictionary.normalize(rawWord)
        database.wordDao().findByLemma(lemma)?.let { word ->
            OfflineWordDictionary.WordCard(
                lemma = word.lemma,
                partOfSpeech = word.partOfSpeech,
                chineseMeaning = word.chineseMeaning,
                englishDefinition = word.simpleDefinition,
                example = word.example,
                cefrLevel = word.cefrLevel,
                source = word.source,
            )
        } ?: OfflineWordDictionary.lookup(lemma)
    }

    /** 读取后台任务所需的单课程快照。 */
    suspend fun lesson(lessonId: Long) = withContext(Dispatchers.IO) {
        database.lessonDao().findById(lessonId)
    }

    /** 获取尚未排队的课程 ID。 */
    suspend fun waitingLessonIds() = withContext(Dispatchers.IO) {
        database.lessonDao().waitingForTranscription().map { it.id }
    }

    /** 获取待处理或可能中断的课程 ID。 */
    suspend fun pendingTranscriptionIds() = withContext(Dispatchers.IO) {
        database.lessonDao().pendingTranscriptions().map { it.id }
    }

    /** 按课程状态设置初始进度或完成进度，并写入数据库。 */
    suspend fun markStatus(lessonId: Long, status: LessonStatus) = withContext(Dispatchers.IO) {
        val progress = when (status) {
            LessonStatus.READY -> 100
            LessonStatus.QUEUED, LessonStatus.WAITING_TRANSCRIPTION, LessonStatus.TRANSCRIBING, LessonStatus.FAILED -> 0
        }
        database.lessonDao().updateTranscriptionStatus(lessonId, status, progress)
    }

    /** 把运行中的进度限制在 0–99，完成状态单独写入 100。 */
    suspend fun updateTranscriptionProgress(lessonId: Long, progress: Int) = withContext(Dispatchers.IO) {
        database.lessonDao().updateTranscriptionProgress(lessonId, progress.coerceIn(0, 99))
    }

    /** 将异常信息整理为单行短文本，供课程详情显示。 */
    suspend fun markTranscriptionFailed(lessonId: Long, error: Throwable) = withContext(Dispatchers.IO) {
        val message = (error.message ?: error.javaClass.simpleName)
            .replace(Regex("\\s+"), " ")
            .take(180)
        database.lessonDao().markTranscriptionFailed(lessonId, message)
    }

    /** 在同一事务内清除并替换一门课程的字幕。 */
    suspend fun replaceSubtitles(lessonId: Long, segments: List<SubtitleSegmentEntity>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.subtitleDao().deleteByLesson(lessonId)
            database.subtitleDao().insertAll(segments)
        }
    }

    /** 为缺少中文的字幕写入本地句译或词汇释义，保留已有中文内容。 */
    suspend fun generateOfflineTranslations(lessonId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.subtitleDao().byLesson(lessonId).forEach { segment ->
                if (segment.chineseText.isNullOrBlank()) {
                    database.subtitleDao().updateChineseText(
                        segment.id,
                        OfflineEnglishChineseTranslator.translate(segment.englishText),
                    )
                }
            }
        }
    }
}
