package com.example.listenenglish.subtitle

import com.example.listenenglish.transcription.parseWhisperSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖 Whisper 子词合并、旧字幕估算以及错误数据降级。 */
class WordTimingTest {
    /** 验证多字节弯引号不会打乱词的位置，并保留停顿。 */
    @Test fun mergesWhisperTokensAcrossUtf8ApostropheAndKeepsPauses() {
        val packed = "100\t1500\t4,150,290;7,290,310;8,310,500;11,700,1000;12,1000,1020;\n Don’t go."
        val segment = checkNotNull(parseWhisperSegment(packed))
        val timing = WordTiming.forSubtitle(segment.englishText, segment.startMs, segment.endMs, segment.wordTimings)

        assertFalse(timing.estimated)
        assertEquals(listOf("Don’t", "go"), timing.words.map { segment.englishText.substring(it.range) })
        assertEquals(listOf(150L, 700L), timing.words.map { it.startMs })
        assertEquals(timing.words.first(), timing.activeAt(499))
        assertNull(timing.activeAt(500))
        assertEquals(timing.words.last(), timing.activeAt(700))
        assertNull(timing.activeAt(1000))
    }

    /** 验证无词级时间的字幕按句内区间估算，且重复词仍独立高亮。 */
    @Test fun oldAndImportedSubtitlesHaveBoundedEstimatedWordTiming() {
        val timing = WordTiming.forSubtitle("One, two two!", 2000, 3200, null)

        assertTrue(timing.estimated)
        assertEquals(listOf(2000L, 2400L, 2800L), timing.words.map { it.startMs })
        assertEquals(timing.words[2], timing.activeAt(2800))
        assertNull(timing.activeAt(3200))
    }

    /** 验证无效原生时间不会作为真实词级区间使用。 */
    @Test fun invalidNativeRangesFallBackToEstimates() {
        val packed = "0\t1000\t3,-10,-10;6,500,750;\nOne go"
        val segment = checkNotNull(parseWhisperSegment(packed))
        assertNull(segment.wordTimings)
        assertTrue(WordTiming.forSubtitle(segment.englishText, 0, 1000, segment.wordTimings).estimated)
    }
}
