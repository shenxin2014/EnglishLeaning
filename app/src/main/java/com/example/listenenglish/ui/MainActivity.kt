package com.example.listenenglish.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.listenenglish.R
import com.example.listenenglish.databinding.ActivityMainBinding

/** 承载课程库、详情和收藏界面，持有页面间共用的 Media3 播放器。 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: LearningViewModel by viewModels()
    private var player: ExoPlayer? = null
    private val playbackHandler = Handler(Looper.getMainLooper())
    private val playbackListeners = mutableSetOf<(String?, Long) -> Unit>()
    /** 在主线程读取播放器位置并驱动已注册界面的字幕同步。 */
    private val playbackTicker = object : Runnable {
        /** 将当前媒体 URI 和毫秒位置通知订阅者，再安排下次更新。 */
        override fun run() {
            val activePlayer = player
            if (activePlayer != null && playbackListeners.isNotEmpty()) {
                val uri = activePlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                playbackListeners.toList().forEach { it(uri, activePlayer.currentPosition) }
            }
            playbackHandler.postDelayed(this, PLAYBACK_TICK_MS)
        }
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importAudio)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    /** 绑定导航和文件选择器，申请通知权限并打开课程库。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.importAudioButton.setOnClickListener {
            audioPicker.launch(arrayOf("audio/mpeg", "audio/*"))
        }
        binding.libraryTab.setOnClickListener { showLibrary() }
        binding.collectionTab.setOnClickListener { showCollections() }
        showLibrary()
        playbackHandler.post(playbackTicker)
    }

    /** 载入指定音频，从默认起点播放，并更新正在播放提示。 */
    fun play(uri: String, title: String) {
        player?.release()
        player = ExoPlayer.Builder(this).build().also {
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.play()
        }
        binding.nowPlaying.text = getString(R.string.now_playing, title)
        binding.nowPlaying.isVisible = true
    }

    /** 从给定的字幕时间戳播放，按媒体 URI 决定复用播放器还是载入新音频。 */
    fun playFrom(uri: String, title: String, positionMs: Long) {
        val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != uri) {
            player?.release()
            player = ExoPlayer.Builder(this).build().also {
                // Supplying the start position with the media item prevents prepare() from
                // resetting an early seek back to 0 on a newly created player.
                it.setMediaItem(MediaItem.fromUri(uri), positionMs.coerceAtLeast(0))
                it.prepare()
                it.play()
            }
            binding.nowPlaying.text = getString(R.string.now_playing, title)
            binding.nowPlaying.isVisible = true
        } else {
            player?.seekTo(positionMs.coerceAtLeast(0))
            player?.play()
        }
    }

    /** 若音频相同则切换暂停和播放，否则打开指定音频。 */
    fun togglePlayback(uri: String, title: String) {
        val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != uri) {
            play(uri, title)
        } else if (player?.isPlaying == true) {
            player?.pause()
        } else {
            player?.play()
        }
    }

    /** 将当前媒体定位到给定的非负毫秒时间。 */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    /** 订阅当前媒体和时间轴更新，供课程详情同步字幕。 */
    fun addPlaybackListener(listener: (String?, Long) -> Unit) {
        playbackListeners += listener
    }

    /** 取消字幕播放监听，避免访问已销毁的视图。 */
    fun removePlaybackListener(listener: (String?, Long) -> Unit) {
        playbackListeners -= listener
    }

    /** 打开课程详情，并可指定收藏或字幕对应的播放起点。 */
    fun openLesson(lessonId: Long, startMs: Long? = null) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, LessonDetailFragment.newInstance(lessonId, startMs))
            .addToBackStack("lesson")
            .commit()
    }

    /** 在主内容区域切换到课程库。 */
    private fun showLibrary() {
        binding.libraryTab.isChecked = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, LibraryFragment())
            .commit()
    }

    /** 在主内容区域切换到收藏复习。 */
    private fun showCollections() {
        binding.collectionTab.isChecked = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, CollectionFragment())
            .commit()
    }

    /** 界面不可见时暂停播放器。 */
    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    /** 取消位置更新，清理监听并释放播放器。 */
    override fun onDestroy() {
        playbackHandler.removeCallbacks(playbackTicker)
        playbackListeners.clear()
        player?.release()
        player = null
        super.onDestroy()
    }

    /** 定义逐词高亮使用的播放器位置更新间隔。 */
    private companion object { const val PLAYBACK_TICK_MS = 50L }
}
