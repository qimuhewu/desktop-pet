import Foundation

/// 对话引擎（纯逻辑，与 Android domain/DialogueEngine.kt 镜像实现）。
///
/// 算法唯一事实源：docs/04-数据模型与养成规则规范.md §4。
/// 语料唯一来源：shared/assets/dialogue_zh.json。
///
/// 匹配规则：优先级降序 → 同优先级内「精确匹配」优先于「关键词包含」→ 全部未命中走 fallback；
/// 空输入直接走点按互动语料（tapReactions）。
///
/// 随机选择通过 `pickIndex` 注入（默认系统随机），镜像单测注入固定索引/序列保证双端一致（DC-05）。
final class DialogueEngine {

    /// 单条对话规则（对应 dialogue_zh.json 的 rules[]）。
    struct DialogueRule: Codable {
        var id: String
        var keywords: [String]
        var priority: Int
        var responses: [String]
    }

    /// 语料集合（对应 dialogue_zh.json 全文）。
    /// 仅需解码（语料只从 JSON 载入，导出存档不含语料），故声明 Decodable 而非 Codable。
    struct DialogueCorpus: Decodable {
        var rules: [DialogueRule] = []
        var fallback: [String] = []
        var tapReactions: [String] = []
        var feedReactionFull: String = "已经吃得很饱啦，再吃要变成小团子了……"
        var feedReactionNormal: String = "谢谢投喂！{food}超好吃的！"

        private enum CodingKeys: String, CodingKey {
            case rules, fallback, tapReactions, feedReactions
        }

        private enum FeedKeys: String, CodingKey {
            case full, normal
        }

        /// 自定义解码：feedReactions 在 JSON 中为嵌套对象 {full, normal}（与 Android 解析对齐）。
        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            rules = try container.decodeIfPresent([DialogueRule].self, forKey: .rules) ?? []
            fallback = try container.decodeIfPresent([String].self, forKey: .fallback) ?? []
            tapReactions = try container.decodeIfPresent([String].self, forKey: .tapReactions) ?? []
            if let feed = try? container.nestedContainer(keyedBy: FeedKeys.self, forKey: .feedReactions) {
                feedReactionFull = try feed.decodeIfPresent(String.self, forKey: .full)
                    ?? "已经吃得很饱啦，再吃要变成小团子了……"
                feedReactionNormal = try feed.decodeIfPresent(String.self, forKey: .normal)
                    ?? "谢谢投喂！{food}超好吃的！"
            }
        }

        /// 手动构造（测试与兜底用）。
        init(rules: [DialogueRule] = [], fallback: [String] = [], tapReactions: [String] = [],
             feedReactionFull: String = "已经吃得很饱啦，再吃要变成小团子了……",
             feedReactionNormal: String = "谢谢投喂！{food}超好吃的！") {
            self.rules = rules
            self.fallback = fallback
            self.tapReactions = tapReactions
            self.feedReactionFull = feedReactionFull
            self.feedReactionNormal = feedReactionNormal
        }

        /// 资产缺失时的应急兜底语料。
        static func fallback() -> DialogueCorpus {
            DialogueCorpus(
                rules: [],
                fallback: ["喵？我没听懂呢……"],
                tapReactions: ["（歪头看你）"]
            )
        }
    }

    /// 对话结果。
    struct DialogueResult: Equatable {
        var text: String
        var ruleId: String
    }

    private let corpus: DialogueCorpus
    private let pickIndex: (Int) -> Int

    /// - Parameters:
    ///   - corpus: 语料集合
    ///   - pickIndex: 索引选择器（注入以实现可复现随机）
    init(corpus: DialogueCorpus, pickIndex: @escaping (Int) -> Int = { Int.random(in: 0..<$0) }) {
        self.corpus = corpus
        self.pickIndex = pickIndex
    }

    /// 输入归一化：trim + 转小写（中文原样），双端一致。
    private func normalize(_ input: String) -> String {
        input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    /// 安全取一条：集合为空时返回占位文案，绝不抛异常。
    private func pickOne(_ list: [String], _ placeholder: String) -> String {
        list.isEmpty ? placeholder : list[pickIndex(list.count)]
    }

    /// 关键词规则匹配（docs/04 §4 伪代码的逐行实现）。
    /// 输入为空/空白时走 tapReactions（时序图 5.1：respond("") → 随机可爱台词）。
    func respond(_ input: String) -> DialogueResult {
        let normalized = normalize(input)
        if normalized.isEmpty {
            return DialogueResult(
                text: pickOne(corpus.tapReactions, "（歪头看你）"),
                ruleId: "tap_reaction"
            )
        }

        struct Hit {
            let rule: DialogueRule
            let exact: Bool
        }

        var hits: [Hit] = []
        for rule in corpus.rules {
            var exact = false
            var partial = false
            for keyword in rule.keywords {
                let k = normalize(keyword)
                if normalized == k {
                    exact = true
                } else if normalized.contains(k) {
                    partial = true
                }
            }
            if exact || partial {
                hits.append(Hit(rule: rule, exact: exact))
            }
        }

        if hits.isEmpty {
            return DialogueResult(
                text: pickOne(corpus.fallback, "喵？我没听懂呢……"),
                ruleId: "fallback"
            )
        }

        // 优先级降序；同优先级内 exact 优先（稳定排序，保持语料原始顺序）
        let best = hits
            .enumerated()
            .sorted { lhs, rhs in
                if lhs.element.rule.priority != rhs.element.rule.priority {
                    return lhs.element.rule.priority > rhs.element.rule.priority
                }
                if lhs.element.exact != rhs.element.exact {
                    return lhs.element.exact
                }
                return lhs.offset < rhs.offset // 稳定性：保持原顺序
            }
            .first!.element

        return DialogueResult(
            text: pickOne(best.rule.responses, "（歪头看你）"),
            ruleId: best.rule.id
        )
    }

    /// 点按互动随机台词（时序图 5.1：单击宠物 → 随机可爱台词）。
    func pickTapReaction() -> DialogueResult {
        DialogueResult(
            text: pickOne(corpus.tapReactions, "（歪头看你）"),
            ruleId: "tap_reaction"
        )
    }

    /// 喂食反馈：饥饿度 ≥ 阈值视为吃饱，否则普通反馈（{food} 替换为食物名）。
    func pickFeedReaction(hungerAfter: Int, foodName: String, hungerFullThreshold: Int = 90) -> String {
        if hungerAfter >= hungerFullThreshold {
            return corpus.feedReactionFull
        }
        return corpus.feedReactionNormal.replacingOccurrences(of: "{food}", with: foodName)
    }
}
