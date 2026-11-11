package com.example.listenenglish.ui

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.listenenglish.data.SubtitleSegmentEntity
import com.example.listenenglish.databinding.ItemSubtitleBinding
import com.example.listenenglish.subtitle.TimedWord
import com.example.listenenglish.subtitle.WordTiming

/** 渲染字幕卡片和当前播放词，并将点击或长按事件交回课程详情。 */
class SubtitleAdapter(
    private val onPlay: (SubtitleSegmentEntity, Long) -> Unit,
    private val onLongClick: (SubtitleSegmentEntity) -> Unit,
) :
    ListAdapter<SubtitleSegmentEntity, SubtitleAdapter.Holder>(DiffCallback) {
    private var activeSegmentId: Long? = null
    private var activeWord: TimedWord? = null
    private var timingSegment: SubtitleSegmentEntity? = null
    private var activeTiming: WordTiming? = null
    private var showChineseTranslation = false
    /** 加载单条字幕的卡片布局。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSubtitleBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )
    /** 绑定字幕文本、显示状态和交互。 */
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    /** 播放位置变化时只更新高亮，其他变更使用完整绑定。 */
    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PLAYBACK_PAYLOAD }) {
            holder.bindPlayback(getItem(position))
        } else {
            onBindViewHolder(holder, position)
        }
    }

    /** 同步当前字幕和单词，返回当前字幕行是否变化。 */
    fun setPlaybackPosition(segment: SubtitleSegmentEntity?, positionMs: Long): Boolean {
        if (timingSegment != segment) {
            timingSegment = segment
            activeTiming = segment?.timing()
        }
        val id = segment?.id
        val word = activeTiming?.activeAt(positionMs)
        val changedSegment = activeSegmentId != id
        if (!changedSegment && activeWord == word) return false
        val previousIndex = if (changedSegment) positionOf(activeSegmentId) else -1
        val newIndex = positionOf(id)
        activeSegmentId = id
        activeWord = word
        if (previousIndex >= 0) notifyItemChanged(previousIndex, PLAYBACK_PAYLOAD)
        if (newIndex >= 0) notifyItemChanged(newIndex, PLAYBACK_PAYLOAD)
        return changedSegment
    }

    /** 按字幕 ID 查找列表位置，找不到时返回 -1。 */
    fun positionOf(id: Long?): Int = currentList.indexOfFirst { it.id == id }

    /** 控制字幕卡片是否展示已有的中文内容。 */
    fun setShowChineseTranslation(show: Boolean) {
        if (showChineseTranslation == show) return
        showChineseTranslation = show
        notifyDataSetChanged()
    }

    /** 持有单条字幕视图，并缓存该行的词级文本和点击状态。 */
    inner class Holder(private val binding: ItemSubtitleBinding) : RecyclerView.ViewHolder(binding.root) {
        private var wordSpans: SpannableString? = null
        private var tappedWord: TimedWord? = null

        /** 设置整行文字和词级点击区域，同时保留句首播放和长按收藏。 */
        fun bind(item: SubtitleSegmentEntity) = with(binding) {
            val timing = item.timing()
            val time = "%02d:%02d".format(item.startMs / 60_000, (item.startMs / 1_000) % 60)
            timestamp.text = if (timing.estimated) "$time · 词级估算" else time
            wordSpans = SpannableString(item.englishText).apply {
                timing.words.forEach { word ->
                    setSpan(object : ClickableSpan() {
                        /** 允许辅助功能按单词触发跳播。 */
                        override fun onClick(widget: View) = onPlay(item, word.startMs)
                        /** 让可点击单词继承字幕样式，将视觉强调交给播放高亮。 */
                        override fun updateDrawState(ds: TextPaint) = Unit
                    }, word.range.first, word.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            chineseText.text = item.chineseText.orEmpty()
            chineseText.visibility = if (showChineseTranslation && !item.chineseText.isNullOrBlank()) View.VISIBLE else View.GONE
            bindPlayback(item)
            tappedWord = null
            englishText.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL -> tappedWord = null
                    MotionEvent.ACTION_UP -> {
                        val offset = englishText.wordOffsetAt(event.x, event.y)
                        tappedWord = timing.words.firstOrNull { offset in it.range }
                    }
                }
                false
            }
            englishText.setOnClickListener {
                val startMs = tappedWord?.startMs ?: item.startMs
                tappedWord = null
                onPlay(item, startMs)
            }
            root.setOnClickListener { onPlay(item, item.startMs) }
            val save = View.OnLongClickListener {
                onLongClick(item)
                true
            }
            englishText.setOnLongClickListener(save)
            root.setOnLongClickListener(save)
        }

        /** 更新当前字幕背景和当前词高亮，复用已绑定的文本。 */
        fun bindPlayback(item: SubtitleSegmentEntity) = with(binding) {
            val isActive = item.id == activeSegmentId
            root.setCardBackgroundColor(Color.parseColor(if (isActive) "#DCEEFF" else "#FFFFFF"))
            root.strokeWidth = if (isActive) 2 else 0
            root.strokeColor = Color.parseColor("#1565C0")
            englishText.text = wordSpans?.let { base ->
                val word = activeWord
                if (!isActive || word == null || word.range.last >= base.length) return@let base
                SpannableString(base).apply {
                    setSpan(BackgroundColorSpan(Color.parseColor("#175795")),
                        word.range.first, word.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(Color.WHITE),
                        word.range.first, word.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    /** 将字幕的持久化时间数据转换为可交互的词级区间。 */
    private fun SubtitleSegmentEntity.timing() = WordTiming.forSubtitle(englishText, startMs, endMs, wordTimings)

    /** 将点击坐标映射为排版后的字符位置，点击文字区域外时返回 -1。 */
    private fun TextView.wordOffsetAt(touchX: Float, touchY: Float): Int {
        val textLayout = layout ?: return -1
        val x = touchX - totalPaddingLeft + scrollX
        val y = touchY - totalPaddingTop + scrollY
        if (y < 0 || y >= textLayout.height) return -1
        val line = textLayout.getLineForVertical(y.toInt())
        if (x < textLayout.getLineLeft(line) || x >= textLayout.getLineRight(line)) return -1
        val caret = textLayout.getOffsetForHorizontal(line, x)
        // Layout returns the nearest caret. A tap in the right half of a letter
        // can resolve to the caret immediately after the letter.
        return if (caret > textLayout.getLineStart(line) && x < textLayout.getPrimaryHorizontal(caret)) {
            caret - 1
        } else {
            caret
        }
    }

    /** 保存播放局部刷新标识和字幕差异比较规则。 */
    private companion object {
        const val PLAYBACK_PAYLOAD = "subtitle_playback"
        /** 按字幕 ID 比较条目身份，并比较实际内容以决定是否重绑。 */
        val DiffCallback = object : DiffUtil.ItemCallback<SubtitleSegmentEntity>() {
            /** 用字幕主键识别同一字幕行。 */
            override fun areItemsTheSame(old: SubtitleSegmentEntity, new: SubtitleSegmentEntity) = old.id == new.id
            /** 文本、翻译或时间区间变化时触发字幕重绑。 */
            override fun areContentsTheSame(old: SubtitleSegmentEntity, new: SubtitleSegmentEntity) = old == new
        }
    }
}
