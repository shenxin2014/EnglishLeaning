package com.example.listenenglish.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 覆盖双语和纯英文 SRT 的时间轴与文本解析。 */
class SrtParserTest {
    /** 验证英文、中文和毫秒时间可保存在同一字幕段。 */
    @Test fun `parses english and chinese subtitle into one segment`() {
        val result = SrtParser.parse(
            lessonId = 7,
            source = """
                1
                00:00:01,250 --> 00:00:03,800
                I would like a cup of tea.
                我想喝一杯茶。
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals(7, result.single().lessonId)
        assertEquals(1_250, result.single().startMs)
        assertEquals(3_800, result.single().endMs)
        assertEquals("I would like a cup of tea.", result.single().englishText)
        assertEquals("我想喝一杯茶。", result.single().chineseText)
    }

    /** 验证纯英文字幕保留空的中文字段。 */
    @Test fun `uses null translation for english only segment`() {
        val result = SrtParser.parse(1, "00:00:00.000 --> 00:00:01.000\nHello!")
        assertEquals("Hello!", result.single().englishText)
        assertNull(result.single().chineseText)
    }
}
