package com.example.listenenglish.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 Room 文本列与 Kotlin 枚举之间可以无损往返。 */
class RoomConvertersTest {
    private val converters = RoomConverters()

    /** 验证字幕就绪状态写入数据库后可还原。 */
    @Test fun `lesson status has lossless database conversion`() {
        assertEquals(LessonStatus.READY, converters.toLessonStatus(converters.fromLessonStatus(LessonStatus.READY)))
    }

    /** 验证短语收藏类型写入数据库后可还原。 */
    @Test fun `collection type has lossless database conversion`() {
        assertEquals(CollectionType.PHRASE, converters.toCollectionType(converters.fromCollectionType(CollectionType.PHRASE)))
    }
}
