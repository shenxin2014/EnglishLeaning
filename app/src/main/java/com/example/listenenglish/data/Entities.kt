package com.example.listenenglish.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 课程字幕从待转写到就绪或失败的持久化状态。 */
enum class LessonStatus { WAITING_TRANSCRIPTION, QUEUED, TRANSCRIBING, READY, FAILED }

/** 收藏条目的内容类型，用于表单选择和列表展示。 */
enum class CollectionType { WORD, PHRASE, SENTENCE }

/** 离线词库记录，按规范词形保存词性、双语释义、例句和来源。 */
@Entity(tableName = "words")
data class WordEntryEntity(
    @PrimaryKey val lemma: String,
    val partOfSpeech: String,
    val simpleDefinition: String,
    val chineseMeaning: String,
    val example: String,
    val cefrLevel: String = "A2-B1",
    val source: String = "内置学习词典",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 课程库条目，保存原始音频 URI、媒体信息和转写状态。 */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long = 0,
    val status: LessonStatus = LessonStatus.WAITING_TRANSCRIPTION,
    val transcriptionProgress: Int = 0,
    val transcriptionError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 课程中的一个字幕段，包含绝对毫秒区间、文本和可选的词级时间数据。 */
@Entity(
    tableName = "subtitle_segments",
    foreignKeys = [ForeignKey(
        entity = LessonEntity::class,
        parentColumns = ["id"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("lessonId")],
)
data class SubtitleSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val startMs: Long,
    val endMs: Long,
    val englishText: String,
    val chineseText: String? = null,
    /** 按英文词序保存绝对毫秒的 start:end 区间，以分号分隔。 */
    val wordTimings: String? = null,
)

/** 学习收藏，含双语释义和可选的课程 ID、回听起点与语境。 */
@Entity(
    tableName = "saved_entries",
    foreignKeys = [ForeignKey(
        entity = LessonEntity::class,
        parentColumns = ["id"],
        childColumns = ["lessonId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("lessonId")],
)
data class SavedEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: CollectionType,
    val expression: String,
    val englishDefinition: String = "",
    val chineseMeaning: String = "",
    val lessonId: Long? = null,
    val startMs: Long? = null,
    val context: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val nextReviewAt: Long = System.currentTimeMillis(),
)
