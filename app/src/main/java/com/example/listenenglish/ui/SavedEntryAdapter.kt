package com.example.listenenglish.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.listenenglish.data.SavedEntryEntity
import com.example.listenenglish.databinding.ItemSavedEntryBinding

/** 渲染收藏词句、双语释义和例句，并处理回听入口。 */
class SavedEntryAdapter(
    private val onEntryClick: (SavedEntryEntity) -> Unit = {},
) : ListAdapter<SavedEntryEntity, SavedEntryAdapter.Holder>(DiffCallback) {
    /** 创建单条收藏卡片视图。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSavedEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )
    /** 绑定对应收藏及其点击处理。 */
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), onEntryClick)

    /** 持有一条收藏的文本视图和点击入口。 */
    class Holder(private val binding: ItemSavedEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        /** 填充收藏内容、缺省释义提示和可选例句。 */
        fun bind(item: SavedEntryEntity, onEntryClick: (SavedEntryEntity) -> Unit) = with(binding) {
            expression.text = item.expression
            entryType.text = item.type.name.lowercase().replaceFirstChar(Char::titlecase)
            englishDefinition.text = item.englishDefinition.ifBlank { "暂未填写英英释义" }
            chineseMeaning.text = item.chineseMeaning.ifBlank { "暂未填写中文释义" }
            exampleText.text = item.context.takeIf { it.isNotBlank() }?.let { "例句：$it" }.orEmpty()
            exampleText.visibility = if (item.context.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            root.setOnClickListener { onEntryClick(item) }
        }
    }

    /** 为收藏列表提供差异比较。 */
    private companion object {
        /** 按收藏 ID 匹配条目，完整比较内容以更新发生变化的卡片。 */
        val DiffCallback = object : DiffUtil.ItemCallback<SavedEntryEntity>() {
            /** 用收藏主键识别同一条记录。 */
            override fun areItemsTheSame(old: SavedEntryEntity, new: SavedEntryEntity) = old.id == new.id
            /** 比较词卡、例句及回听来源等完整内容。 */
            override fun areContentsTheSame(old: SavedEntryEntity, new: SavedEntryEntity) = old == new
        }
    }
}
