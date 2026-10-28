package com.mobiledesktoppet.app.domain

import kotlin.random.Random

/**
 * 对话引擎（纯逻辑，与 iOS Domain/DialogueEngine.swift 镜像实现）。
 *
 * 算法唯一事实源：docs/04-数据模型与养成规则规范.md §4。
 * 语料唯一来源：shared/assets/dialogue_zh.json。
 *
 * 匹配规则：优先级降序 → 同优先级内「精确匹配」优先于「关键词包含」→ 全部未命中走 fallback；
 * 空输入直接走点按互动语料（tapReactions）。
 *
 * 随机选择通过 [pickIndex] 注入（默认系统随机），镜像单测注入固定索引/序列保证双端一致（DC-05）。
 */
class DialogueEngine(
    private val corpus: DialogueCorpus,
    private val pickIndex: (Int) -> Int = { Random.Default.nextInt(it) }
) {

    /** 单条对话规则（对应 dialogue_zh.json 的 rules[]）。 */
    data class DialogueRule(
        val id: String,
        val keywords: List<String>,
        val priority: Int,
        val responses: List<String>
    )

    /** 语料集合（对应 dialogue_zh.json 全文）。 */
    data class DialogueCorpus(
        val rules: List<DialogueRule> = emptyList(),
        val fallback: List<String> = emptyList(),
        val tapReactions: List<String> = emptyList(),
        val feedReactionFull: String = "已经吃得很饱啦，再吃要变成小团子了……",
        val feedReactionNormal: String = "谢谢投喂！{food}超好吃的！"
    ) {
        companion object {
            /** 资产缺失时的应急兜底语料。 */
            fun fallback(): DialogueCorpus = DialogueCorpus(
                rules = emptyList(),
                fallback = listOf("喵？我没听懂呢……"),
                tapReactions = listOf("（歪头看你）")
            )
        }
    }

    /** 对话结果。 */
    data class DialogueResult(
        val text: String,
        val ruleId: String
    )

    /** 输入归一化：trim + 仅 ASCII 转小写（中文原样），双端一致。 */
    private fun normalize(input: String): String = input.trim().lowercase()

    /** 安全取一条：集合为空时返回占位文案，绝不抛异常。 */
    private fun pickOne(list: List<String>, placeholder: String): String =
        if (list.isEmpty()) placeholder else list[pickIndex(list.size)]

    /**
     * 关键词规则匹配（docs/04 §4 伪代码的逐行实现）。
     * 输入为空/空白时走 tapReactions（时序图 5.1：respond("") → 随机可爱台词）。
     */
    fun respond(input: String): DialogueResult {
        val normalized = normalize(input)
        if (normalized.isEmpty()) {
            return DialogueResult(
                text = pickOne(corpus.tapReactions, "（歪头看你）"),
                ruleId = "tap_reaction"
            )
        }

        // 收集命中规则：exact = 整句等于关键词；partial = 包含关键词
        data class Hit(val rule: DialogueRule, val exact: Boolean)

        val hits = ArrayList<Hit>()
        for (rule in corpus.rules) {
            var exact = false
            var partial = false
            for (keyword in rule.keywords) {
                val k = normalize(keyword)
                if (normalized == k) exact = true
                else if (normalized.contains(k)) partial = true
            }
            if (exact || partial) hits.add(Hit(rule, exact))
        }

        if (hits.isEmpty()) {
            return DialogueResult(
                text = pickOne(corpus.fallback, "喵？我没听懂呢……"),
                ruleId = "fallback"
            )
        }

        // 优先级降序；同优先级内 exact 优先（sortedWith 为稳定排序，保持语料原始顺序）
        val best = hits.sortedWith(
            compareByDescending<Hit> { it.rule.priority }.thenByDescending { it.exact }
        ).first()

        return DialogueResult(
            text = pickOne(best.rule.responses, "（歪头看你）"),
            ruleId = best.rule.id
        )
    }

    /** 点按互动随机台词（时序图 5.1：单击宠物 → 随机可爱台词）。 */
    fun pickTapReaction(): DialogueResult = DialogueResult(
        text = pickOne(corpus.tapReactions, "（歪头看你）"),
        ruleId = "tap_reaction"
    )

    /** 喂食反馈：饥饿度 ≥ 阈值视为吃饱，否则普通反馈（{food} 替换为食物名）。 */
    fun pickFeedReaction(hungerAfter: Int, foodName: String, hungerFullThreshold: Int = 90): String {
        return if (hungerAfter >= hungerFullThreshold) {
            corpus.feedReactionFull
        } else {
            corpus.feedReactionNormal.replace("{food}", foodName)
        }
    }
}
