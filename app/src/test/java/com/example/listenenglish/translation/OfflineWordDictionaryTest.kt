package com.example.listenenglish.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖离线查词的输入归一化和未收录提示。 */
class OfflineWordDictionaryTest {
    /** 验证英文弯引号所有格可查到原始词形。 */
    @Test fun `normalizes possessive word before lookup`() {
        val card = OfflineWordDictionary.lookup("Volunteer’s")

        assertEquals("volunteer", card.lemma)
        assertEquals("志愿者；自愿参加", card.chineseMeaning)
        assertTrue(card.isKnown)
    }

    /** 验证未收录词返回明确的状态和界面提示。 */
    @Test fun `unknown word is clearly marked instead of pretending to be translated`() {
        val card = OfflineWordDictionary.lookup("unlistedword")

        assertFalse(card.isKnown)
        assertEquals("本地词典暂未收录", card.chineseMeaning)
    }
}
