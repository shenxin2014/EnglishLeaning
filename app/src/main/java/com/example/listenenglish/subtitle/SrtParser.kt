package com.example.listenenglish.subtitle

import com.example.listenenglish.data.SubtitleSegmentEntity

/** 读取 CRLF 或 LF 换行的本地 SRT，以首行作为英文并将后续文本作为中文。 */
object SrtParser {
    /** 将有效 SRT 时间块转换为课程字幕段，首行作为英文，其余行合并为中文。 */
    fun parse(lessonId: Long, source: String): List<SubtitleSegmentEntity> {
        val blocks = source.replace("\r\n", "\n").trim().split(Regex("\\n\\s*\\n"))
        return blocks.mapNotNull { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            val timingIndex = lines.indexOfFirst { "-->" in it }
            if (timingIndex < 0 || lines.size <= timingIndex + 1) return@mapNotNull null
            val timing = lines[timingIndex].split("-->")
            if (timing.size != 2) return@mapNotNull null
            val start = parseTimestamp(timing[0].trim()) ?: return@mapNotNull null
            val end = parseTimestamp(timing[1].trim()) ?: return@mapNotNull null
            val textLines = lines.drop(timingIndex + 1)
            SubtitleSegmentEntity(
                lessonId = lessonId,
                startMs = start,
                endMs = end,
                englishText = textLines.first(),
                chineseText = textLines.drop(1).joinToString("\n").ifBlank { null },
            )
        }
    }

    /** 读取逗号或小数点分隔的 SRT 时间戳，换算成毫秒。 */
    private fun parseTimestamp(value: String): Long? {
        val match = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})").matchEntire(value) ?: return null
        val (hours, minutes, seconds, millis) = match.destructured
        return hours.toLong() * 3_600_000 + minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + millis.toLong()
    }
}
