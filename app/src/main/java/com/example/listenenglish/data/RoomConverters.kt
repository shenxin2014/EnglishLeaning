package com.example.listenenglish.data

import androidx.room.TypeConverter

/** 在 Kotlin 枚举和 Room 的文本列之间转换，数据库保存枚举名称。 */
class RoomConverters {
    /** 将课程状态编码为枚举名。 */
    @TypeConverter fun fromLessonStatus(value: LessonStatus) = value.name
    /** 从数据库的枚举名还原课程状态。 */
    @TypeConverter fun toLessonStatus(value: String) = LessonStatus.valueOf(value)
    /** 将收藏类别编码为枚举名。 */
    @TypeConverter fun fromCollectionType(value: CollectionType) = value.name
    /** 从数据库的枚举名还原收藏类别。 */
    @TypeConverter fun toCollectionType(value: String) = CollectionType.valueOf(value)
}
