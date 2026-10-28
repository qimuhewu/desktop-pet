package com.mobiledesktoppet.app.domain

import com.mobiledesktoppet.app.domain.GrowthEngine.FoodItem
import com.mobiledesktoppet.app.domain.GrowthEngine.GrowthRules
import com.mobiledesktoppet.app.domain.GrowthEngine.GrowthState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 成长引擎镜像单测（用例编号见 tests/README.md，与 iOS GrowthEngineTests.swift 一一对应）。
 * 数值常量与 shared/assets/growth_rules.json v1 一致（来源标注）。
 */
class GrowthEngineTest {

    private val rules = GrowthRules() // 与 growth_rules.json v1 同值

    // 食物（与 foods.json 同值）
    private val driedFish = FoodItem("dried_fish", "小鱼干", 15, 8, 8, 15, "dried_fish")
    private val bento = FoodItem("bento", "便当", 40, 5, 2, 10, "bento")
    private val cake = FoodItem("cake", "蛋糕", 20, 15, 5, 12, "cake")

    /** GC-01 喂食小鱼干：hunger 80→95，mood 60→68，intimacy 50→58，exp 0→15。 */
    @Test
    fun gc01_feedDriedFish() {
        val state = GrowthState(level = 1, exp = 0, hunger = 80.0, mood = 60.0, intimacy = 50.0)
        val result = GrowthEngine.checkLevelUp(GrowthEngine.applyFeed(state, driedFish, rules), rules)
        assertEquals(95.0, result.hunger, 1e-9)
        assertEquals(68.0, result.mood, 1e-9)
        assertEquals(58.0, result.intimacy, 1e-9)
        assertEquals(15, result.exp)
        assertEquals(1, result.level)
    }

    /** GC-02 喂食溢出封顶：hunger 90 + 便当(+40) → 100。 */
    @Test
    fun gc02_feedClampOverflow() {
        val state = GrowthState(level = 1, exp = 0, hunger = 90.0, mood = 60.0, intimacy = 50.0)
        val result = GrowthEngine.applyFeed(state, bento, rules)
        assertEquals(100.0, result.hunger, 1e-9)
    }

    /** GC-03 闲置 24h 衰减：hunger 80-48=32，mood 60-36=24，intimacy 50-12=38。 */
    @Test
    fun gc03_decay24h() {
        val now = 1_000_000_000_000L
        val state = GrowthState(level = 1, exp = 0, hunger = 80.0, mood = 60.0, intimacy = 50.0)
        val result = GrowthEngine.applyElapsedDecay(state, now - 24 * 3_600_000L, now, rules)
        assertEquals(32.0, result.hunger, 1e-9)
        assertEquals(24.0, result.mood, 1e-9)
        assertEquals(38.0, result.intimacy, 1e-9)
    }

    /** GC-04 闲置 24h 后喂食蛋糕：先衰减再增益（hunger 32+20=52）。 */
    @Test
    fun gc04_decayThenFeed() {
        val now = 1_000_000_000_000L
        val state = GrowthState(level = 1, exp = 0, hunger = 80.0, mood = 60.0, intimacy = 50.0)
        val decayed = GrowthEngine.applyElapsedDecay(state, now - 24 * 3_600_000L, now, rules)
        val fed = GrowthEngine.applyFeed(decayed, cake, rules)
        assertEquals(52.0, fed.hunger, 1e-9)
    }

    /** GC-05 升级：exp 95 + 小鱼干(+15)=110 ≥ 100 → level 2，exp 10。 */
    @Test
    fun gc05_levelUp() {
        val state = GrowthState(level = 1, exp = 95, hunger = 80.0, mood = 60.0, intimacy = 50.0)
        val fed = GrowthEngine.applyFeed(state, driedFish, rules)
        val leveled = GrowthEngine.checkLevelUp(fed, rules)
        assertEquals(2, leveled.level)
        assertEquals(10, leveled.exp)
    }

    /**
     * GC-06 连升验证（按 foods.json：便当 expGain=10；QA 严过关修正：原断言误按每次 +30 计算）。
     * 依据 docs/04 §3 升级曲线 expToNextLevel(L) = 100 + (L-1)*50 独立重算。
     */
    @Test
    fun gc06_multiLevelUp() {
        // 三次便当：exp=10,20,30，均 < expToNext(1)=100 → level 1
        var state = GrowthState(level = 1, exp = 0, hunger = 80.0, mood = 60.0, intimacy = 50.0)
        repeat(3) {
            state = GrowthEngine.checkLevelUp(GrowthEngine.applyFeed(state, bento, rules), rules)
        }
        assertEquals(1, state.level)
        assertEquals(30, state.exp)
        // 连升两级：注入 exp=250（从 Lv1）：250≥100 → Lv2(150)；150≥expToNext(2)=150 → Lv3(0)
        val doubleUp = GrowthEngine.checkLevelUp(state.copy(exp = 250), rules)
        assertEquals(3, doubleUp.level)
        assertEquals(0, doubleUp.exp)
        // 连升三级未满：注入 exp=600（从 Lv1）：600≥100 → Lv2(500)；500≥150 → Lv3(350)；
        // 350≥200 → Lv4(150)；150 < expToNext(4)=250 停止 → Lv4/exp150
        val jump = GrowthEngine.checkLevelUp(state.copy(exp = 600), rules)
        assertEquals(4, jump.level)
        assertEquals(150, jump.exp)
    }

    /** GC-07 满级 99：等级不再增长，经验封顶为 expToNextLevel(99)-1（= 100+98*50-1 = 4999）。 */
    @Test
    fun gc07_maxLevelCap() {
        val state = GrowthState(level = 99, exp = 0, hunger = 80.0, mood = 60.0, intimacy = 50.0)
        // 满级继续喂食：exp 10 < 4999，未触及封顶
        val fed = GrowthEngine.applyFeed(state, bento, rules)
        val capped = GrowthEngine.checkLevelUp(fed, rules)
        assertEquals(99, capped.level)
        assertEquals(10, capped.exp)
        // 注入超上限经验验证封顶：exp=9999 → 封顶 4999
        val overCap = GrowthEngine.checkLevelUp(state.copy(exp = 9999), rules)
        assertEquals(99, overCap.level)
        assertEquals(4999, overCap.exp)
    }

    /** 附加：升级曲线公式 expToNextLevel(L) = 100 + (L-1)*50。 */
    @Test
    fun expCurveFormula() {
        assertEquals(100, GrowthEngine.expToNextLevel(1, rules))
        assertEquals(150, GrowthEngine.expToNextLevel(2, rules))
        assertEquals(4999 + 1, GrowthEngine.expToNextLevel(99, rules))
    }
}
