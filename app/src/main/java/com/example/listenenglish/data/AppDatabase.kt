package com.example.listenenglish.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.example.listenenglish.translation.OfflineWordDictionary

/** 定义课程、字幕、词库和收藏的 Room 模式，并维护旧数据库迁移。 */
@Database(
    entities = [LessonEntity::class, SubtitleSegmentEntity::class, SavedEntryEntity::class, WordEntryEntity::class],
    version = 5,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    /** 获取课程和转写状态访问接口。 */
    abstract fun lessonDao(): LessonDao
    /** 获取离线词条访问接口。 */
    abstract fun wordDao(): WordDao
    /** 获取收藏访问接口。 */
    abstract fun savedEntryDao(): SavedEntryDao
    /** 获取字幕访问接口。 */
    abstract fun subtitleDao(): SubtitleDao

    /** 集中注册各版本迁移和首次建库所需的词库数据。 */
    companion object {
        /** 为旧课程补充转写进度字段。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            /** 给迁移前的课程使用 0 作为默认转写进度。 */
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE lessons ADD COLUMN transcriptionProgress INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** 为课程增加可为空的转写失败原因。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            /** 为已有课程保留空的失败原因，后续失败时再写入。 */
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE lessons ADD COLUMN transcriptionError TEXT")
            }
        }
        /** 新增本地词库表并写入内置词条。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            /** 建立词库所需列和规范词形主键，再导入内置数据。 */
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS words (
                        lemma TEXT NOT NULL,
                        partOfSpeech TEXT NOT NULL,
                        simpleDefinition TEXT NOT NULL,
                        chineseMeaning TEXT NOT NULL,
                        example TEXT NOT NULL,
                        cefrLevel TEXT NOT NULL,
                        source TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(lemma)
                    )
                """.trimIndent())
                seedBuiltInWords(database)
            }
        }

        /** 新增可为空的词级时间字段，已有字幕可按句内时间估算。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            /** 新增时间列并保持原有字幕文本、译文和句级时间。 */
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE subtitle_segments ADD COLUMN wordTimings TEXT")
            }
        }

        /** 全新安装时在建库后初始化内置词条。 */
        val SEED_CALLBACK = object : Callback() {
            /** 在 Room 创建好各张表后写入种子词条。 */
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedBuiltInWords(db)
            }
        }

        /** 写入内置词条，并保留主键已存在的记录。 */
        private fun seedBuiltInWords(database: SupportSQLiteDatabase) {
            OfflineWordDictionary.allCards().forEach { card ->
                database.execSQL(
                    """INSERT OR IGNORE INTO words
                       (lemma, partOfSpeech, simpleDefinition, chineseMeaning, example, cefrLevel, source, updatedAt)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                    arrayOf(card.lemma, card.partOfSpeech, card.englishDefinition, card.chineseMeaning,
                        card.example, card.cefrLevel, card.source, System.currentTimeMillis()),
                )
            }
        }
    }
}
