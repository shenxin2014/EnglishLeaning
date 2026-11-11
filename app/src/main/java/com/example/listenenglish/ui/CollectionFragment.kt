package com.example.listenenglish.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.listenenglish.R
import com.example.listenenglish.data.CollectionType
import com.example.listenenglish.databinding.DialogAddCollectionBinding
import com.example.listenenglish.databinding.FragmentCollectionBinding
import com.example.listenenglish.translation.OfflineWordDictionary

/** 展示收藏复习列表，提供离线查词、手动收藏和原声回听入口。 */
class CollectionFragment : Fragment() {
    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by activityViewModels()
    private val adapter = SavedEntryAdapter(::openEntrySource)

    /** 创建收藏页布局和绑定。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 连接收藏数据、查词按钮与手动添加入口。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.collectionList.layoutManager = LinearLayoutManager(requireContext())
        binding.collectionList.adapter = adapter
        binding.addCollectionButton.setOnClickListener { showAddDialog() }
        binding.lookupWordButton.setOnClickListener { showLookupInput() }
        viewModel.savedEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            binding.emptyCollections.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 打开有来源的收藏对应的课程，并从保存位置播放。 */
    private fun openEntrySource(entry: com.example.listenenglish.data.SavedEntryEntity) {
        val lessonId = entry.lessonId
        val startMs = entry.startMs
        if (lessonId == null || startMs == null) {
            Toast.makeText(requireContext(), "这条收藏没有关联课程音频", Toast.LENGTH_SHORT).show()
            return
        }
        (activity as? MainActivity)?.openLesson(lessonId, startMs)
    }

    /** 接受用户输入的英文单词并发起离线查询。 */
    private fun showLookupInput() {
        val input = EditText(requireContext()).apply {
            hint = "输入英文单词，例如 volunteer"
            setSingleLine(true)
            setPadding(48, 12, 48, 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("离线查词")
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("查询") { _, _ ->
                input.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { word ->
                    viewModel.lookupWord(word, ::showLookupResult)
                }
            }
            .show()
    }

    /** 显示本地词卡或未收录提示，并提供收藏操作。 */
    private fun showLookupResult(card: OfflineWordDictionary.WordCard) {
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 8)
            addView(detailText("${card.lemma}  ${card.partOfSpeech}".trim(), 20f, true))
            addView(detailText("英英：${card.englishDefinition}", 16f))
            addView(detailText("中文：${card.chineseMeaning}", 16f))
            if (card.example.isNotBlank()) addView(detailText("例句：${card.example}", 15f))
            addView(detailText("${if (card.isKnown) "来源：${card.source} · ${card.cefrLevel}" else "提示：未收录词可先收藏并手动补充释义"}", 13f))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (card.isKnown) "离线词典" else "未收录词")
            .setView(content)
            .setNegativeButton("关闭", null)
            .setPositiveButton("收藏") { _, _ -> showWordSaveDialog(card) }
            .show()
    }

    /** 创建词卡对话框内具有统一间距的文字行。 */
    private fun detailText(text: String, size: Float, bold: Boolean = false) = TextView(requireContext()).apply {
        this.text = text
        textSize = size
        setPadding(0, 8, 0, 8)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    /** 以查询结果预填可编辑的单词收藏表单。 */
    private fun showWordSaveDialog(card: OfflineWordDictionary.WordCard) {
        val dialogBinding = DialogAddCollectionBinding.inflate(layoutInflater).apply {
            typeSpinner.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.collection_types),
            )
            typeSpinner.setSelection(0)
            expressionInput.setText(card.lemma)
            englishDefinitionInput.setText(card.englishDefinition)
            chineseMeaningInput.setText(card.chineseMeaning)
            exampleInput.setText(card.example)
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
                    context = dialogBinding.exampleInput.text?.toString().orEmpty(),
                )
            }
            .show()
    }

    /** 手动创建单词、短语或整句收藏。 */
    private fun showAddDialog() {
        val dialogBinding = DialogAddCollectionBinding.inflate(layoutInflater)
        val labels = resources.getStringArray(R.array.collection_types)
        dialogBinding.typeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_collection)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val expression = dialogBinding.expressionInput.text?.toString().orEmpty()
                if (expression.isNotBlank()) {
                    val type = when (dialogBinding.typeSpinner.selectedItemPosition) {
                        1 -> CollectionType.PHRASE
                        2 -> CollectionType.SENTENCE
                        else -> CollectionType.WORD
                    }
                    viewModel.saveEntry(
                        type,
                        expression,
                        dialogBinding.englishDefinitionInput.text?.toString().orEmpty(),
                        dialogBinding.chineseMeaningInput.text?.toString().orEmpty(),
                        context = dialogBinding.exampleInput.text?.toString().orEmpty(),
                    )
                }
            }
            .show()
    }

    /** 释放已销毁的收藏页视图绑定。 */
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
