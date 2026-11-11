package com.example.listenenglish

import android.app.Application
import androidx.room.Room
import com.example.listenenglish.data.AppDatabase
import com.example.listenenglish.data.LearningRepository

/** 应用级依赖入口，创建共享的 Room 数据库和仓库供界面及后台任务使用。 */
class ListenEnglishApplication : Application() {
    /** 首次访问时打开数据库，注册各版本迁移并在建库时初始化词库。 */
    val repository: LearningRepository by lazy {
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "listen_english.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .addCallback(AppDatabase.SEED_CALLBACK)
            .fallbackToDestructiveMigration()
            .build()
        LearningRepository(this, database)
    }
}
