package com.example.listenenglish.translation

/**
 * 为离线精听提供中文辅助理解。先查询预置句译，找不到对应整句时提供已收录的词汇释义。
 */
object OfflineEnglishChineseTranslator {
    /** 优先返回已收录的整句译文，未命中时给出带标识的词汇释义。 */
    fun translate(english: String): String {
        val normalized = normalize(english)
        return sentenceTranslations[normalized] ?: vocabularyGloss(english)
    }

    /** 提取已收录的词汇释义，并标明这是词汇提示。 */
    private fun vocabularyGloss(english: String): String {
        val entries = english
            .lowercase()
            .split(Regex("[^a-z]+"))
            .mapNotNull { word -> vocabulary[word]?.let { "$word：$it" } }
            .distinct()
            .take(8)
        return if (entries.isEmpty()) "本地词典暂未收录这句话。" else "本地词汇释义：${entries.joinToString("；")}"
    }

    /** 归一化大小写、标点与空白以匹配预置句库。 */
    private fun normalize(text: String) = text.lowercase().trim().replace(Regex("[^a-z0-9]+"), " ").trim()

    private val sentenceTranslations = mapOf(
        "maya had lived in greenfield for only three months when she saw a poster at the bus stop" to "玛雅在格林菲尔德只住了三个月，就在公交车站看到了一张海报。",
        "it said that the local library needed volunteers for its saturday children s program" to "海报上说，当地图书馆的周六儿童活动需要志愿者。",
        "maya liked books and she wanted to meet more people in the neighborhood" to "玛雅喜欢书，也想认识社区里更多的人。",
        "so she decided to help on saturday morning she arrived at the library at 10 o clock" to "所以她决定去帮忙。周六早上十点，她来到图书馆。",
        "the building was old but bright there were large windows colorful posters and many shelves" to "这栋楼虽然旧，却很明亮。有大窗户、彩色海报和许多书架。",
        "full of books at the front desk a librarian named mr lewis welcomed her" to "书架上摆满了书。前台一位叫刘易斯先生的图书管理员欢迎了她。",
        "thank you for coming he said today we have a reading activity for children from six to" to "“谢谢你来，”他说，“今天我们为六岁到……的孩子举办阅读活动。”",
        "nine years old your job is simple please help them choose books listen when they read" to "……九岁的孩子举办活动。你的工作很简单：帮他们选书，在他们朗读时倾听。",
        "and make sure everyone feels included" to "并确保每个人都能融入其中。",
        "maya felt a little nervous she had never worked with children before" to "玛雅有点紧张，她以前从没和孩子们一起工作过。",
        "what if they don t like me she asked mr lewis smiled just be patient ask them questions" to "“要是他们不喜欢我怎么办？”她问。刘易斯先生笑了：“耐心一点，问他们问题。”",
        "children usually enjoy talking about things they love" to "孩子们通常喜欢谈论自己热爱的事物。",
        "at half past 10 the children started to arrive some came with their parents" to "十点半，孩子们开始到来。有些和父母一起来。",
        "while others came with an older brother or sister there were 12 children in total" to "还有些是和哥哥或姐姐一起来的。一共有十二个孩子。",
        "a few were very excited and ran toward the picture book area others stood quietly near the" to "有几个孩子非常兴奋，跑向图画书区；其他人则安静地站在……附近。",
        "door and looked around maya first met a boy called ben he was holding a book about space" to "门边四处张望。玛雅最先认识了一个叫本的男孩，他拿着一本关于太空的书。",
        "do you like planets she asked yes ben said i want to be an astronaut" to "“你喜欢行星吗？”她问。“喜欢，”本说，“我想成为宇航员。”",
        "that sounds exciting maya replied which planet would you like to visit" to "“听起来真令人兴奋，”玛雅回答，“你想去哪个星球？”",
        "maybe mars he said but i don t want to go alone" to "“也许是火星，”他说，“但我不想一个人去。”",
        "maya laughed then you need a good team" to "玛雅笑了：“那你需要一个好团队。”",
        "soon more children gathered around them one girl said she liked animals another child said" to "很快，更多孩子围在他们身边。一个女孩说她喜欢动物，另一个孩子说……",
        "he wanted to read a story about pirates maya helped each child find a book she did not know every" to "他想读一个关于海盗的故事。玛雅帮每个孩子找书；她并不知道所有书名。",
        "title so she showed them how to use the library computer to search for books by topic" to "于是她教他们如何用图书馆电脑按主题搜索图书。",
        "after that the group sat on a large blue carpet mr lewis read a short story about a small" to "之后，大家坐在一块大蓝地毯上。刘易斯先生读了一个关于一只小……的短故事。",
        "bird that was afraid to fly then he asked the children to talk about a time when they had felt" to "害怕飞翔的小鸟的故事。随后他请孩子们谈谈自己曾经感到……的时候。",
        "afraid at first nobody answered the room became very quiet maya remembered mr lewis" to "害怕的经历。一开始没有人回答，房间里变得非常安静。玛雅想起了刘易斯先生的……",
        "advice so she spoke first when i was younger i was afraid of speaking english in class" to "建议，于是她先开口：“我小时候害怕在课堂上说英语。”",
        "she said i worried that i would make mistakes but my teacher told me that mistakes are part" to "她说：“我担心自己会犯错，但我的老师告诉我，犯错是……的一部分。”",
        "of learning a girl named sophia raised her hand i m afraid of swimming in deep water she said" to "学习的一部分。”一个叫索菲娅的女孩举起手：“我害怕在深水里游泳。”",
        "that is understandable maya said what helps you feel safer" to "“这可以理解，”玛雅说，“什么能让你感觉更安全？”",
        "my dad stays close to me sophia answered then other children began to share their ideas" to "“我爸爸会待在我身边，”索菲娅回答。接着其他孩子也开始分享自己的想法。",
        "ben said he was afraid of dogs because a large dog had jumped on him last year" to "本说他害怕狗，因为去年有一条大狗扑到过他身上。",
        "another boy said he was afraid of sleeping without a light" to "另一个男孩说他害怕关着灯睡觉。",
        "maya listened carefully and thanked each child for speaking later the children worked in pairs" to "玛雅认真倾听，并感谢每个发言的孩子。后来，孩子们两人一组活动。",
        "they had to choose one book and make a small poster about it some drew pictures and others wrote" to "他们要选一本书，并为它制作一张小海报。有些人画画，另一些人写……",
        "short sentences one pair argued because both children wanted to use the same red marker" to "简短的句子。有一组发生了争执，因为两个孩子都想用同一支红色马克笔。",
        "maya helped them solve the problem could one of you use the red marker first" to "玛雅帮他们解决问题：“你们能不能先让其中一个人用红色马克笔？”",
        "and then give it to the other person she asked but i need it now one child said" to "“然后再交给另一个人？”她问。“可我现在就要用，”一个孩子说。",
        "i understand maya replied how about using it for five minutes each we can set a timer" to "“我明白，”玛雅回答，“每人用五分钟怎么样？我们可以设个计时器。”",
        "the children agreed after a few minutes they were working together again" to "孩子们同意了。几分钟后，他们又一起合作了。",
        "at 12 o clock the parents returned the children showed them their posters and borrowed" to "十二点，家长们回来了。孩子们向他们展示海报，并借了……",
        "books to take home mr lewis thanked maya for her help you did very well he said" to "要带回家的书。刘易斯先生感谢玛雅的帮助：“你做得非常好。”",
        "you were calm kind and practical" to "“你冷静、善良，而且很务实。”",
        "maya was tired but she felt happy she had expected to spend the morning with books" to "玛雅虽然累了，却感到很开心。她原以为早上只是和书待在一起。",
        "instead she had learned something more important a library was not only a quiet place to read" to "但相反，她学到了更重要的事：图书馆不只是一个安静阅读的地方。",
        "it was also a place where people could ask questions share ideas and feel welcome" to "它也是一个人们可以提问、分享想法并感到受欢迎的地方。",
        "before leaving maya wrote her name on the volunteer list for the following saturday" to "离开前，玛雅在下一个周六的志愿者名单上写下了自己的名字。",
    )

    private val vocabulary = mapOf(
        "library" to "图书馆", "volunteer" to "志愿者", "children" to "儿童", "book" to "书", "reading" to "阅读",
        "help" to "帮助", "choose" to "选择", "question" to "问题", "answer" to "回答", "afraid" to "害怕",
        "nervous" to "紧张的", "teacher" to "老师", "mistake" to "错误", "learn" to "学习", "parent" to "父母",
        "story" to "故事", "planet" to "行星", "astronaut" to "宇航员", "team" to "团队", "animal" to "动物",
        "computer" to "电脑", "search" to "搜索", "quiet" to "安静的", "share" to "分享", "idea" to "想法",
        "together" to "一起", "happy" to "开心的", "important" to "重要的", "welcome" to "受欢迎的",
    )
}
