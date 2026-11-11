package com.example.listenenglish.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.listenenglish.R
import com.example.listenenglish.data.CollectionType
import com.example.listenenglish.data.SubtitleSegmentEntity
import com.example.listenenglish.databinding.DialogAddCollectionBinding
import com.example.listenenglish.databinding.FragmentLessonDetailBinding
import com.example.listenenglish.translation.OfflineWordDictionary

/** 协调单课播放器、逐句及逐词字幕、离线翻译和字幕收藏。 */
class LessonDetailFragment : Fragment() {
    private var _binding: FragmentLessonDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by activityViewModels()
    private val lessonId get() = requireArguments().getLong(ARG_LESSON_ID)
    private val subtitleAdapter = SubtitleAdapter(::playSegment, ::showSaveOptions)
    private var currentLessonUri: String? = null
    private var currentLessonTitle: String = ""
    private var pendingStartMs: Long? = null
    private val playbackListener: (String?, Long) -> Unit = { uri, positionMs ->
        if (uri != null && uri == currentLessonUri) updateActiveSubtitle(positionMs)
        else subtitleAdapter.setPlaybackPosition(null, positionMs)
    }

    private val srtPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val content = requireContext().contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        viewModel.importSrt(lessonId, content)
    }

    /** 创建课程详情的视图绑定。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLessonDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 绑定播放和导入按钮，并观察当前课程、翻译开关和字幕数据。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.subtitleList.layoutManager = LinearLayoutManager(requireContext())
        binding.subtitleList.adapter = subtitleAdapter
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.importSrtButton.setOnClickListener {
            srtPicker.launch(arrayOf("application/x-subrip", "text/*", "application/octet-stream"))
        }
        pendingStartMs = arguments?.getLong(ARG_START_MS, NO_START_MS)?.takeIf { it != NO_START_MS }
        binding.offlineTranslationSwitch.setOnCheckedChangeListener { _, enabled ->
            viewModel.setOfflineTranslationEnabled(lessonId, enabled)
        }
        (activity as? MainActivity)?.addPlaybackListener(playbackListener)

        viewModel.offlineTranslationEnabled.observe(viewLifecycleOwner) { enabled ->
            if (binding.offlineTranslationSwitch.isChecked != enabled) {
                binding.offlineTranslationSwitch.isChecked = enabled
            }
            subtitleAdapter.setShowChineseTranslation(enabled)
        }

        viewModel.lesson(lessonId).observe(viewLifecycleOwner) { lesson ->
            lesson ?: return@observe
            binding.lessonTitle.text = lesson.title
            currentLessonUri = lesson.sourceUri
            currentLessonTitle = lesson.title
            binding.lessonStatus.text = lesson.status.label(lesson.transcriptionProgress)
            binding.transcriptionError.isVisible = lesson.status == com.example.listenenglish.data.LessonStatus.FAILED && !lesson.transcriptionError.isNullOrBlank()
            binding.transcriptionError.text = lesson.transcriptionError?.let { "失败原因：$it" }.orEmpty()
            binding.transcriptionProgress.isVisible = lesson.status == com.example.listenenglish.data.LessonStatus.TRANSCRIBING || lesson.status == com.example.listenenglish.data.LessonStatus.QUEUED
            binding.transcriptionProgress.progress = lesson.transcriptionProgress
            binding.playPauseButton.setOnClickListener {
                (activity as? MainActivity)?.togglePlayback(lesson.sourceUri, lesson.title)
            }
            binding.rewindButton.setOnClickListener { (activity as? MainActivity)?.seekTo(0) }
            pendingStartMs?.let { startMs ->
                pendingStartMs = null
                (activity as? MainActivity)?.playFrom(lesson.sourceUri, lesson.title, startMs)
            }
        }
        viewModel.subtitles(lessonId).observe(viewLifecycleOwner) { segments ->
            subtitleAdapter.submitList(segments)
            binding.emptySubtitles.visibility = if (segments.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 按句首或单词的起点开始播放，并立即更新高亮。 */
    private fun playSegment(segment: SubtitleSegmentEntity, startMs: Long) {
        val lessonUri = currentLessonUri ?: return
        (activity as? MainActivity)?.playFrom(lessonUri, currentLessonTitle, startMs)
        subtitleAdapter.setPlaybackPosition(segment, startMs)
    }

    /** 从播放位置找出当前字幕，句子切换且不在视口内时滚动到该行。 */
    private fun updateActiveSubtitle(positionMs: Long) {
        val active = subtitleAdapter.currentList.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
        if (!subtitleAdapter.setPlaybackPosition(active, positionMs) || active == null) return
        val index = subtitleAdapter.positionOf(active.id)
        if (index < 0) return
        val manager = binding.subtitleList.layoutManager as? LinearLayoutManager ?: return
        val isVisible = index in manager.findFirstVisibleItemPosition()..manager.findLastVisibleItemPosition()
        if (!isVisible) binding.subtitleList.smoothScrollToPosition(index)
    }

    /** 长按字幕时提供单词和整句收藏入口。 */
    private fun showSaveOptions(segment: SubtitleSegmentEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("收藏内容")
            .setItems(arrayOf("收藏单词", "收藏整句")) { _, which ->
                if (which == 0) showWordPicker(segment) else saveSegment(segment)
            }
            .show()
    }

    /** 提取字幕中的去重候选词，并过滤常见停用词。 */
    private fun showWordPicker(segment: SubtitleSegmentEntity) {
        val words = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")
            .findAll(segment.englishText)
            .map { it.value.lowercase() }
            .filter { it.length > 1 && it !in commonWords }
            .distinct()
            .toList()
        if (words.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle("选择要收藏的单词")
            .setItems(words.toTypedArray()) { _, index -> saveWord(words[index], segment) }
            .show()
    }

    /** 离线查询所选单词，再用查询结果预填收藏表单。 */
    private fun saveWord(word: String, segment: SubtitleSegmentEntity) {
        viewModel.lookupWord(word) { card -> showWordSaveDialog(card, segment) }
    }

    /** 展示可编辑的双语词卡，保存例句、课程和原字幕起点。 */
    private fun showWordSaveDialog(card: OfflineWordDictionary.WordCard, segment: SubtitleSegmentEntity) {
        val dialogBinding = DialogAddCollectionBinding.inflate(layoutInflater).apply {
            typeSpinner.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.collection_types),
            )
            typeSpinner.setSelection(0)
            expressionInput.setText(card.lemma)
            englishDefinitionInput.setText(card.englishDefinition)
            chineseMeaningInput.setText(card.chineseMeaning)
            exampleInput.setText(card.example.ifBlank { segment.englishText })
        }
        AlertDialog.Builder(requireContext())
            .setTitle("收藏单词")
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.saveEntry(
                    type = CollectionType.WORD,
                    expression = dialogBinding.expressionInput.text?.toString().orEmpty(),
                    englishDefinition = dialogBinding.englishDefinitionInput.text?.toString().orEmpty(),
                    chineseMeaning = dialogBinding.chineseMeaningInput.text?.toString().orEmpty(),
                    lessonId = lessonId,
                    startMs = segment.startMs,
                    context = dialogBinding.exampleInput.text?.toString().orEmpty(),
                )
            }
            .show()
    }

    /** 用当前字幕和可用译文预填收藏表单，保留原音频位置。 */
    private fun saveSegment(segment: SubtitleSegmentEntity) {
        val dialogBinding = DialogAddCollectionBinding.inflate(layoutInflater).apply {
            typeSpinner.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.collection_types),
            )
            typeSpinner.setSelection(2)
            expressionInput.setText(segment.englishText)
            chineseMeaningInput.setText(segment.chineseText.orEmpty())
            exampleInput.setText(segment.englishText)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.save_to_collection)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val types = listOf(CollectionType.WORD, CollectionType.PHRASE, CollectionType.SENTENCE)
                viewModel.saveEntry(
                    type = types[dialogBinding.typeSpinner.selectedItemPosition],
                    expression = dialogBinding.expressionInput.text?.toString().orEmpty(),
                    englishDefinition = dialogBinding.englishDefinitionInput.text?.toString().orEmpty(),
                    chineseMeaning = dialogBinding.chineseMeaningInput.text?.toString().orEmpty(),
                    lessonId = lessonId,
                    startMs = segment.startMs,
                    context = dialogBinding.exampleInput.text?.toString().orEmpty(),
                )
            }
            .show()
    }

    /** 将转写状态和进度转换为详情页文案。 */
    private fun com.example.listenenglish.data.LessonStatus.label(progress: Int) = when (this) {
        com.example.listenenglish.data.LessonStatus.WAITING_TRANSCRIPTION -> "等待离线转写"
        com.example.listenenglish.data.LessonStatus.QUEUED -> "等待转写队列"
        com.example.listenenglish.data.LessonStatus.TRANSCRIBING -> "正在离线转写 $progress%"
        com.example.listenenglish.data.LessonStatus.READY -> "字幕已就绪"
        com.example.listenenglish.data.LessonStatus.FAILED -> "转写失败（可查看原因后重新导入或导入 SRT）"
    }

    /** 解除播放器监听并释放详情页绑定。 */
    override fun onDestroyView() {
        (activity as? MainActivity)?.removePlaybackListener(playbackListener)
        _binding = null
        super.onDestroyView()
    }

    /** 定义课程详情参数和字幕取词时过滤的常见词。 */
    companion object {
        private const val ARG_LESSON_ID = "lesson_id"
        private const val ARG_START_MS = "start_ms"
        private const val NO_START_MS = -1L
        private val commonWords = setOf("a", "an", "the", "and", "or", "to", "of", "in", "on", "at", "for", "with", "from", "that", "this", "it", "is", "was", "were", "be", "had", "has", "have", "she", "he", "they", "we", "i", "you", "her", "his", "their", "my", "your", "as", "but", "so", "when", "then")
        /** 使用课程 ID 和可选的回听起点构造可恢复的详情参数。 */
        fun newInstance(lessonId: Long, startMs: Long? = null) = LessonDetailFragment().apply {
            arguments = bundleOf(ARG_LESSON_ID to lessonId, ARG_START_MS to (startMs ?: NO_START_MS))
        }
    }
}
