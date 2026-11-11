package com.example.listenenglish.translation

/** 提供内置学习词卡，用于初始化 Room 词库并支持本地查词。 */
object OfflineWordDictionary {
    /** 供词卡和收藏使用的离线查询结果，包含命中标志、释义和例句。 */
    data class WordCard(
        val lemma: String,
        val partOfSpeech: String,
        val chineseMeaning: String,
        val englishDefinition: String,
        val example: String,
        val cefrLevel: String = "A2-B1",
        val source: String = "内置学习词典",
        val isKnown: Boolean = true,
    )

    /** 统一大小写和弯引号，去除英文所有格后缀。 */
    fun normalize(word: String): String = word.lowercase().trim()
        .replace('’', '\'')
        .removeSuffix("'s")

    /** 在内置词条中查找规范词形，未命中时返回提示词卡。 */
    fun lookup(word: String): WordCard {
        val key = normalize(word)
        return entries[key] ?: WordCard(
            lemma = key,
            partOfSpeech = "",
            chineseMeaning = "本地词典暂未收录",
            englishDefinition = "A word selected from the current subtitle.",
            example = "",
            source = "当前字幕",
            isKnown = false,
        )
    }

    /** 返回用于初始化 Room 词库的内置词条。 */
    fun allCards(): List<WordCard> = entries.values.toList()

    private val entries = mapOf(
        "library" to card("library", "noun", "图书馆", "A place where people can read or borrow books.", "The local library needed volunteers."),
        "volunteer" to card("volunteer", "noun / verb", "志愿者；自愿参加", "A person who offers to do something without being paid.", "Maya wrote her name on the volunteer list."),
        "poster" to card("poster", "noun", "海报", "A large printed notice or picture put in a public place.", "She saw a poster at the bus stop."),
        "neighborhood" to card("neighborhood", "noun", "街区；社区", "The area where people live near one another.", "She wanted to meet people in the neighborhood."),
        "librarian" to card("librarian", "noun", "图书管理员", "A person who works in and helps people use a library.", "A librarian welcomed her at the front desk."),
        "activity" to card("activity", "noun", "活动", "Something that people do for enjoyment or a purpose.", "They had a reading activity for children."),
        "choose" to card("choose", "verb", "选择", "To decide which person or thing you want.", "Please help them choose books."),
        "included" to card("included", "adjective", "被接纳的；被包括在内的", "Feeling that you are accepted as part of a group.", "Make sure everyone feels included."),
        "nervous" to card("nervous", "adjective", "紧张的", "Worried or slightly afraid about something that may happen.", "Maya felt a little nervous."),
        "patient" to card("patient", "adjective", "耐心的", "Able to stay calm while waiting or dealing with difficulties.", "Just be patient and ask them questions."),
        "planet" to card("planet", "noun", "行星", "A large round object that moves around a star.", "Which planet would you like to visit?"),
        "astronaut" to card("astronaut", "noun", "宇航员", "A person trained to travel and work in space.", "Ben wanted to be an astronaut."),
        "pirate" to card("pirate", "noun", "海盗", "A person who attacks ships and steals things at sea.", "He wanted to read a story about pirates."),
        "topic" to card("topic", "noun", "主题；话题", "A subject that people talk, write, or learn about.", "They searched for books by topic."),
        "afraid" to card("afraid", "adjective", "害怕的", "Feeling fear or worry about something.", "The bird was afraid to fly."),
        "mistake" to card("mistake", "noun", "错误", "Something that is done incorrectly.", "Mistakes are part of learning."),
        "understandable" to card("understandable", "adjective", "可以理解的", "Easy to understand or reasonable in a situation.", "That is understandable, Maya said."),
        "practical" to card("practical", "adjective", "实际的；务实的", "Useful and sensible rather than only theoretical.", "You were calm, kind, and practical."),
        "borrow" to card("borrow", "verb", "借入", "To take and use something that belongs to someone else and return it later.", "The children borrowed books to take home."),
        "welcome" to card("welcome", "verb / adjective", "受欢迎的；欢迎", "To make someone feel pleased to be in a place or group.", "People could feel welcome there."),
    )

    /** 用统一的默认级别和来源构造内置词卡。 */
    private fun card(lemma: String, partOfSpeech: String, chinese: String, english: String, example: String) =
        WordCard(lemma, partOfSpeech, chinese, english, example)
}
