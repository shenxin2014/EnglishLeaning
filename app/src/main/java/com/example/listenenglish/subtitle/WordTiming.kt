package com.example.listenenglish.subtitle

/** 字幕中的一个词，范围使用 Kotlin 字符索引，时间使用音频绝对毫秒。 */
internal data class TimedWord(val range: IntRange, val startMs: Long, val endMs: Long)

/** 解析单句的词级时间并在播放时定位当前词，同时标识估算结果。 */
internal class WordTiming(val words: List<TimedWord>, val estimated: Boolean) {
    /** 返回覆盖该播放位置的单词，区间右端和停顿期间返回空值。 */
    fun activeAt(positionMs: Long): TimedWord? =
        words.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }

    /** 提供词级时间解析、估算和 Whisper 对齐入口。 */
    companion object {
        private val wordPattern = Regex("""[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*""")

        /** 校验持久化的词级区间，缺失或无效时按词长估算句内时间。 */
        fun forSubtitle(text: String, startMs: Long, endMs: Long, stored: String?): WordTiming {
            val ranges = wordPattern.findAll(text).map { it.range }.toList()
            if (ranges.isEmpty() || startMs >= endMs) return WordTiming(emptyList(), false)
            val pairs = stored?.split(';')
            if (pairs != null && pairs.size == ranges.size) {
                val words = pairs.zip(ranges).mapNotNull { (pair, range) ->
                    val start = pair.substringBefore(':').toLongOrNull()
                    val end = pair.substringAfter(':', "").toLongOrNull()
                    if (start == null || end == null || start < startMs || end > endMs || start >= end) {
                        null
                    } else {
                        TimedWord(range, start, end)
                    }
                }
                if (words.size == ranges.size && words.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }) {
                    return WordTiming(words, false)
                }
            }

            val weights = ranges.map { (it.last - it.first + 1).coerceIn(2, 8) }
            val totalWeight = weights.sum()
            var usedWeight = 0
            val words = ranges.zip(weights).map { (range, weight) ->
                val wordStart = startMs + (endMs - startMs) * usedWeight / totalWeight
                usedWeight += weight
                val wordEnd = startMs + (endMs - startMs) * usedWeight / totalWeight
                TimedWord(range, wordStart, wordEnd)
            }
            return WordTiming(words, true)
        }

        /** 按未裁剪原文的 UTF-8 字节范围合并 Whisper 子词，再编码为单词时间区间。 */
        fun fromWhisper(text: String, startMs: Long, endMs: Long, encodedTokens: String): String? {
            if (startMs >= endMs) return null
            var previousOffset = 0
            val tokens = encodedTokens.trimEnd(';').split(';').map { record ->
                val fields = record.split(',')
                if (fields.size != 3) return null
                val offset = fields[0].toIntOrNull() ?: return null
                val start = fields[1].toLongOrNull() ?: return null
                val end = fields[2].toLongOrNull() ?: return null
                if (offset <= previousOffset) return null
                Token(previousOffset, offset, start, end).also { previousOffset = offset }
            }
            if (previousOffset != text.toByteArray(Charsets.UTF_8).size) return null
            var charOffset = 0
            var byteOffset = 0
            var tokenIndex = 0
            var previousEnd = startMs
            val words = mutableListOf<String>()
            for (match in wordPattern.findAll(text)) {
                val byteStart = byteOffset + text.substring(charOffset, match.range.first).utf8Length()
                val byteEnd = byteStart + match.value.utf8Length()
                charOffset = match.range.last + 1
                byteOffset = byteEnd
                while (tokenIndex < tokens.size && tokens[tokenIndex].endOffset <= byteStart) tokenIndex++
                val matching = tokens.asSequence().drop(tokenIndex).takeWhile { it.startOffset < byteEnd }
                    .filter { it.startMs >= 0 && it.endMs >= it.startMs }.toList()
                if (matching.isEmpty()) return null
                val wordStart = matching.first().startMs.coerceAtLeast(previousEnd)
                val wordEnd = matching.last().endMs.coerceAtMost(endMs)
                if (wordStart >= wordEnd) return null
                words += "$wordStart:$wordEnd"
                previousEnd = wordEnd
            }
            return words.takeIf { it.isNotEmpty() }?.joinToString(";")
        }

        /** 取得 Whisper 偏移计算使用的 UTF-8 字节长度。 */
        private fun String.utf8Length(): Int = toByteArray(Charsets.UTF_8).size

        /** Whisper 子词的 UTF-8 字节范围和音频毫秒区间。 */
        private data class Token(val startOffset: Int, val endOffset: Int, val startMs: Long, val endMs: Long)
    }
}
