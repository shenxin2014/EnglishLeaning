package com.example.listenenglish.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 课程列表、单课程查询和转写状态的 Room 访问接口。 */
@Dao
interface LessonDao {
    /** 观察课程库，最近导入的课程排在前面。 */
    @Query("SELECT * FROM lessons ORDER BY createdAt DESC")
    fun observeAll(): LiveData<List<LessonEntity>>

    /** 观察指定课程及其状态变化，课程不存在时返回空值。 */
    @Query("SELECT * FROM lessons WHERE id = :lessonId")
    fun observeById(lessonId: Long): LiveData<LessonEntity?>

    /** 读取课程快照，供后台任务判断是否需要转写。 */
    @Query("SELECT * FROM lessons WHERE id = :lessonId")
    suspend fun findById(lessonId: Long): LessonEntity?

    /** 查找尚未入队的课程。 */
    @Query("SELECT * FROM lessons WHERE status = 'WAITING_TRANSCRIPTION'")
    suspend fun waitingForTranscription(): List<LessonEntity>

    /** 插入课程并返回数据库分配的 ID。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(lesson: LessonEntity): Long

    /** 同步写入状态和进度，并清除上一次失败原因。 */
    @Query("UPDATE lessons SET status = :status, transcriptionProgress = :progress, transcriptionError = NULL WHERE id = :lessonId")
    suspend fun updateTranscriptionStatus(lessonId: Long, status: LessonStatus, progress: Int)

    /** 只更新指定课程的转写进度。 */
    @Query("UPDATE lessons SET transcriptionProgress = :progress WHERE id = :lessonId")
    suspend fun updateTranscriptionProgress(lessonId: Long, progress: Int)

    /** 将课程标记为失败并保存可展示的错误信息。 */
    @Query("UPDATE lessons SET status = 'FAILED', transcriptionProgress = 0, transcriptionError = :message WHERE id = :lessonId")
    suspend fun markTranscriptionFailed(lessonId: Long, message: String)

    /** 返回待处理、排队中或正在转写的课程，供启动时恢复。 */
    @Query("SELECT * FROM lessons WHERE status IN ('WAITING_TRANSCRIPTION', 'QUEUED', 'TRANSCRIBING')")
    suspend fun pendingTranscriptions(): List<LessonEntity>
}

/** 按规范词形查询 Room 中的离线词条。 */
@Dao
interface WordDao {
    /** 精确匹配词库主键，未收录时返回空值。 */
    @Query("SELECT * FROM words WHERE lemma = :lemma LIMIT 1")
    suspend fun findByLemma(lemma: String): WordEntryEntity?
}

/** 收藏条目的查询、写入和删除接口。 */
@Dao
interface SavedEntryDao {
    /** 观察收藏列表，优先按下次复习时间排序。 */
    @Query("SELECT * FROM saved_entries ORDER BY nextReviewAt ASC, createdAt DESC")
    fun observeAll(): LiveData<List<SavedEntryEntity>>

    /** 新增一条收藏并返回其 ID。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SavedEntryEntity): Long

    /** 按 ID 删除指定收藏。 */
    @Query("DELETE FROM saved_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)
}

/** 按课程读写字幕和离线翻译缓存。 */
@Dao
interface SubtitleDao {
    /** 批量写入字幕段，由调用方负责课程级替换事务。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<SubtitleSegmentEntity>)

    /** 清除指定课程的字幕段。 */
    @Query("DELETE FROM subtitle_segments WHERE lessonId = :lessonId")
    suspend fun deleteByLesson(lessonId: Long)

    /** 读取某课程按起始时间排序的字幕快照。 */
    @Query("SELECT * FROM subtitle_segments WHERE lessonId = :lessonId ORDER BY startMs")
    suspend fun byLesson(lessonId: Long): List<SubtitleSegmentEntity>

    /** 观察课程字幕，按时间顺序向界面发送变更。 */
    @Query("SELECT * FROM subtitle_segments WHERE lessonId = :lessonId ORDER BY startMs")
    fun observeByLesson(lessonId: Long): LiveData<List<SubtitleSegmentEntity>>

    /** 只更新指定字幕段的中文内容。 */
    @Query("UPDATE subtitle_segments SET chineseText = :chineseText WHERE id = :segmentId")
    suspend fun updateChineseText(segmentId: Long, chineseText: String)
}
