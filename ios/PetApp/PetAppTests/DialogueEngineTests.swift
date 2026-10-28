import XCTest
@testable import PetApp

/// 对话引擎镜像单测（用例编号见 tests/README.md，与 Android DialogueEngineTest.kt 一一对应）。
/// 语料与 shared/assets/dialogue_zh.json v1 一致（来源标注）。
final class DialogueEngineTests: XCTestCase {

    /// 固定索引注入器：按给定序列循环取索引（DC-05 双端同序列同结果）。
    private func fixedPickIndex(_ indexes: [Int]) -> (Int) -> Int {
        var cursor = 0
        return { size in
            let idx = indexes[cursor % indexes.count]
            cursor += 1
            precondition(idx < size, "注入索引 \(idx) 越界 size=\(size)")
            return idx
        }
    }

    /// 精简语料（结构同 dialogue_zh.json）。
    private var corpus: DialogueEngine.DialogueCorpus {
        DialogueEngine.DialogueCorpus(
            rules: [
                DialogueEngine.DialogueRule(
                    id: "greet_hello",
                    keywords: ["你好", "hello", "hi", "嗨", "哈喽"],
                    priority: 100,
                    responses: ["你好呀~", "你来啦！"]),
                DialogueEngine.DialogueRule(
                    id: "praise",
                    keywords: ["可爱", "漂亮", "喜欢", "爱你"],
                    priority: 90,
                    responses: ["谢谢夸奖~"]),
                DialogueEngine.DialogueRule(
                    id: "thanks",
                    keywords: ["谢谢", "感谢"],
                    priority: 60,
                    responses: ["不客气~"])
            ],
            fallback: ["喵？我没听懂呢……"],
            tapReactions: ["（开心地转了个圈）", "嘿嘿，被你发现啦~"]
        )
    }

    /// DC-01 精确匹配：「你好」命中 greet_hello（priority 100，exact）。
    func testDC01ExactMatch() {
        let engine = DialogueEngine(corpus: corpus, pickIndex: fixedPickIndex([0]))
        let result = engine.respond("你好")
        XCTAssertEqual(result.ruleId, "greet_hello")
    }

    /// DC-02 优先级降序：「你好可爱」同时命中 greet_hello(100,partial) 与 praise(90,partial) → greet_hello。
    func testDC02PriorityDescending() {
        let engine = DialogueEngine(corpus: corpus, pickIndex: fixedPickIndex([0]))
        let result = engine.respond("你好可爱")
        XCTAssertEqual(result.ruleId, "greet_hello")
    }

    /// DC-03 同优先级 exact > partial：两条同优先级规则，整句命中的优先。
    func testDC03ExactBeatsPartialAtSamePriority() {
        let samePriorityCorpus = DialogueEngine.DialogueCorpus(
            rules: [
                DialogueEngine.DialogueRule(id: "partial_rule", keywords: ["喜欢"], priority: 90, responses: ["partial 命中"]),
                DialogueEngine.DialogueRule(id: "exact_rule", keywords: ["喜欢你"], priority: 90, responses: ["exact 命中"])
            ],
            fallback: ["fallback"],
            tapReactions: ["tap"]
        )
        let engine = DialogueEngine(corpus: samePriorityCorpus, pickIndex: fixedPickIndex([0]))
        let result = engine.respond("喜欢你")
        XCTAssertEqual(result.ruleId, "exact_rule")
    }

    /// DC-04 未命中 → fallback；空输入 → tapReactions。
    func testDC04FallbackAndTap() {
        let engine = DialogueEngine(corpus: corpus, pickIndex: fixedPickIndex([0]))
        let miss = engine.respond("今天天气怎么样")
        XCTAssertEqual(miss.ruleId, "fallback")
        XCTAssertEqual(miss.text, "喵？我没听懂呢……")

        let tap = engine.respond("")
        XCTAssertEqual(tap.ruleId, "tap_reaction")
        XCTAssertEqual(tap.text, "（开心地转了个圈）")

        let blank = engine.respond("   ")
        XCTAssertEqual(blank.ruleId, "tap_reaction")
    }

    /// DC-05 随机选择可注入：固定序列 → 双端同序列同结果。
    func testDC05InjectedIndexSequence() {
        let engine = DialogueEngine(corpus: corpus, pickIndex: fixedPickIndex([1, 0]))
        let first = engine.respond("你好")
        let second = engine.respond("谢谢")
        XCTAssertEqual(first.text, "你来啦！")
        XCTAssertEqual(second.text, "不客气~")
    }

    /// 附加：normalize 大小写不敏感（ASCII 转小写）。
    func testNormalizeCaseInsensitive() {
        let engine = DialogueEngine(corpus: corpus, pickIndex: fixedPickIndex([0]))
        XCTAssertEqual(engine.respond("  HELLO ").ruleId, "greet_hello")
    }

    /// 附加：pickFeedReaction 满腹阈值切换。
    func testFeedReactionThreshold() {
        let engine = DialogueEngine(corpus: corpus, pickIndex: fixedPickIndex([0]))
        XCTAssertTrue(engine.pickFeedReaction(hungerAfter: 95, foodName: "蛋糕").contains("饱"))
        XCTAssertTrue(engine.pickFeedReaction(hungerAfter: 60, foodName: "蛋糕").contains("蛋糕"))
    }
}
