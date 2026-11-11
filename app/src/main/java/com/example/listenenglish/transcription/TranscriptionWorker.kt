package com.example.listenenglish.transcription

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.listenenglish.ListenEnglishApplication
import com.example.listenenglish.data.LessonStatus
import com.example.listenenglish.data.SubtitleSegmentEntity
import android.net.Uri
import kotlinx.coroutines.runBlocking

/** 运行可由 WorkManager 跟踪的离线转写，并将进度和字幕写入 Room。 */
class TranscriptionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    /** 读取任务的课程 ID，在前台识别音频并保存结果或失败信息。 */
    override suspend fun doWork(): Result {
        val lessonId = inputData.getLong(KEY_LESSON_ID, -1)
        if (lessonId < 0) return Result.failure()
        val repository = (applicationContext as ListenEnglishApplication).repository

        val lesson = repository.lesson(lessonId) ?: return Result.failure()
        if (lesson.status == LessonStatus.READY) return Result.success()
        val startedAt = SystemClock.elapsedRealtime()
        var lastLoggedProgress = -10
        return try {
            setForeground(createForegroundInfo(lesson.title, 0))
            Log.i(TAG, "worker started: lessonId=$lessonId title=${lesson.title} uri=${lesson.sourceUri}")
            repository.markStatus(lessonId, LessonStatus.TRANSCRIBING)
            val segments = WhisperCppTranscriptionEngine(applicationContext)
                .transcribe(Uri.parse(lesson.sourceUri)) { progress ->
                    runBlocking {
                        repository.updateTranscriptionProgress(lessonId, progress)
                        setForeground(createForegroundInfo(lesson.title, progress))
                    }
                    if (progress >= lastLoggedProgress + 10 || progress == 100) {
                        lastLoggedProgress = progress
                        Log.i(TAG, "worker progress: lessonId=$lessonId progress=$progress% elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                    }
                }
                .map { result ->
                    SubtitleSegmentEntity(
                        lessonId = lessonId,
                        startMs = result.startMs,
                        endMs = result.endMs,
                        englishText = result.englishText,
                        wordTimings = result.wordTimings,
                    )
                }
            repository.replaceSubtitles(lessonId, segments)
            repository.markStatus(lessonId, LessonStatus.READY)
            Log.i(TAG, "worker completed: lessonId=$lessonId segments=${segments.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "worker failed: lessonId=$lessonId elapsedMs=${SystemClock.elapsedRealtime() - startedAt}", error)
            repository.markTranscriptionFailed(lessonId, error)
            Result.failure()
        }
    }

    /** 在前台任务开始前返回准备状态的通知。 */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("正在准备离线转写", 0)
    }

    /** 创建或更新转写进度通知，并按 Android 版本声明前台服务类型。 */
    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "离线转写", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val content = if (progress == 0) "正在准备：$title" else "正在转写：$title（$progress%）"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("英语 MP3 精听：离线转写")
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** 定义课程入参、通知渠道和转写日志常量。 */
    companion object {
        const val KEY_LESSON_ID = "lesson_id"
        private const val CHANNEL_ID = "offline_transcription"
        private const val NOTIFICATION_ID = 3001
        private const val TAG = "TranscriptionWorker"
    }
}
