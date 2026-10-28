package com.mobiledesktoppet.app.domain

import com.mobiledesktoppet.app.domain.DialogueEngine.DialogueCorpus
import com.mobiledesktoppet.app.domain.DialogueEngine.DialogueRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话引擎镜像单测（用例编号见 tests/README.md，与 iOS DialogueEngineTests.swift 一一对应）。
 * 语料与 shared/assets/dialogue_zh.json v1 一致（来源标注）。
 */
class DialogueEngineTest {

    /** 固定索引注入器：按给定序列循环取索引（DC-05 双端同序列同结果）。 */
    private fun fixedPickIndex(vararg indexes: Int): (Int) -> Int {
        var cursor = 0
        return { size ->
            val idx = indexes[cursor % indexes.size]
            cursor += 1
            check(idx < size) { "注入索引 $idx 越界 size=$size" }
            idx
        }
    }

    /** 精简语料（结构同 dialogue_zh.json）。 */
    private val corpus = DialogueCorpus(
        rules = listOf(
            DialogueRule("greet_hello", listOf("你好", "hello", "hi", "嗨", "哈喽"), 100, listOf("你好呀~", "你来啦！")),
            DialogueRule("praise", listOf("可爱", "漂亮", "喜欢", "爱你"), 90, listOf("谢谢夸奖~")),
            DialogueRule("thanks", listOf("谢谢", "感谢"), 60, listOf("不客气~"))
        ),
        fallback = listOf("喵？我没听懂呢……"),
        tapReactions = listOf("（开心地转了个圈）", "嘿嘿，被你发现啦~")
    )

    /** DC-01 精确匹配：「你好」命中 greet_hello（priority 100，exact）。 */
    @Test
    fun dc01_exactMatch() {
        val engine = DialogueEngine(corpus, fixedPickIndex(0))
        val result = engine.respond("你好")
        assertEquals("greet_hello", result.ruleId)
    }

    /** DC-02 优先级降序：「你好可爱」同时命中 greet_hello(100,partial) 与 praise(90,partial) → greet_hello。 */
    @Test
    fun dc02_priorityDescending() {
        val engine = DialogueEngine(corpus, fixedPickIndex(0))
        val result = engine.respond("你好可爱")
        assertEquals("greet_hello", result.ruleId)
    }

    /** DC-03 同优先级 exact > partial：两条同优先级规则，整句命中的优先。 */
    @Test
    fun dc03_exactBeatsPartialAtSamePriority() {
        val samePriorityCorpus = DialogueCorpus(
            rules = listOf(
                DialogueRule("partial_rule", listOf("喜欢"), 90, listOf("partial 命中")),
                DialogueRule("exact_rule", listOf("喜欢你"), 90, listOf("exact 命中"))
            ),
            fallback = listOf("fallback"),
            tapReactions = listOf("tap")
        )
        val engine = DialogueEngine(samePriorityCorpus, fixedPickIndex(0))
        val result = engine.respond("喜欢你")
        assertEquals("exact_rule", result.ruleId)
    }

    /** DC-04 未命中 → fallback；空输入 → tapReactions。 */
    @Test
    fun dc04_fallbackAndTap() {
        val engine = DialogueEngine(corpus, fixedPickIndex(0))
        val miss = engine.respond("今天天气怎么样")
        assertEquals("fallback", miss.ruleId)
        assertEquals("喵？我没听懂呢……", miss.text)

        val tap = engine.respond("")
        assertEquals("tap_reaction", tap.ruleId)
        assertEquals("（开心地转了个圈）", tap.text)

        val blank = engine.respond("   ")
        assertEquals("tap_reaction", blank.ruleId)
    }

    /** DC-05 随机选择可注入：固定序列 → 双端同序列同结果。 */
    @Test
    fun dc05_injectedIndexSequence() {
        val engine = DialogueEngine(corpus, fixedPickIndex(1, 0))
        val first = engine.respond("你好")
        val second = engine.respond("谢谢")
        assertEquals("你来啦！", first.text)
        assertEquals("不客气~", second.text)
    }

    /** 附加：normalize 大小写不敏感（ASCII 转小写）。 */
    @Test
    fun normalizeCaseInsensitive() {
        val engine = DialogueEngine(corpus, fixedPickIndex(0))
        assertEquals("greet_hello", engine.respond("  HELLO ").ruleId)
    }

    /** 附加：pickFeedReaction 满腹阈值切换。 */
    @Test
    fun feedReactionThreshold() {
        val engine = DialogueEngine(corpus, fixedPickIndex(0))
        assertTrue(engine.pickFeedReaction(95, "蛋糕").contains("饱"))
        assertTrue(engine.pickFeedReaction(60, "蛋糕").contains("蛋糕"))
    }
}
