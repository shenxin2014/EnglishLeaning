package com.example.listenenglish.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.listenenglish.R
import com.example.listenenglish.data.LessonEntity
import com.example.listenenglish.data.LessonStatus
import com.example.listenenglish.databinding.ItemLessonBinding

/** 将课程信息和转写状态绑定到可点击的课程库列表项。 */
class LessonAdapter(private val onClick: (LessonEntity) -> Unit) :
    ListAdapter<LessonEntity, LessonAdapter.Holder>(DiffCallback) {
    /** 加载单个课程卡片的布局。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )
    /** 将当前位置的课程交给列表项绑定。 */
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    /** 持有课程卡片视图并将点击事件交给课程库。 */
    inner class Holder(private val binding: ItemLessonBinding) : RecyclerView.ViewHolder(binding.root) {
        /** 展示标题、音频时长和转写状态。 */
        fun bind(item: LessonEntity) = with(binding) {
            lessonTitle.text = item.title
            lessonMeta.text = root.context.getString(R.string.lesson_meta, duration(item.durationMs), item.status.label(item.transcriptionProgress))
            root.setOnClickListener { onClick(item) }
        }
    }

    /** 将毫秒格式化为分和秒。 */
    private fun duration(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
    /** 生成课程卡片上的转写状态摘要。 */
    private fun LessonStatus.label(progress: Int) = when (this) {
        LessonStatus.WAITING_TRANSCRIPTION -> "等待离线转写"
        LessonStatus.QUEUED -> "等待转写队列"
        LessonStatus.TRANSCRIBING -> "正在离线转写 $progress%"
        LessonStatus.READY -> "字幕已就绪"
        LessonStatus.FAILED -> "转写失败"
    }

    /** 为课程列表提供差异比较。 */
    private companion object {
        /** 按数据库 ID 判断课程身份，按记录内容判断刷新。 */
        val DiffCallback = object : DiffUtil.ItemCallback<LessonEntity>() {
            /** 用课程主键识别同一行。 */
            override fun areItemsTheSame(old: LessonEntity, new: LessonEntity) = old.id == new.id
            /** 课程内容或转写进度变化时刷新该行。 */
            override fun areContentsTheSame(old: LessonEntity, new: LessonEntity) = old == new
        }
    }
}
