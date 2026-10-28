# 双端镜像一致性测试约定

## 目标

保证 PRD P1-3「跨端养成数据一致」：同一份输入在 Android（Kotlin）与 iOS（Swift）上产出**完全一致**的数值与文案选择。

## 实现

- 业务引擎 `GrowthEngine` / `DialogueEngine` / `ReminderPlanner` 为纯逻辑类（无平台依赖），
  双端按 `docs/04-数据模型与养成规则规范.md` 的伪代码逐行镜像实现。
- 同一组测试用例在两端各实现一遍，用例编号一一对应：

| 用例编号 | 内容 |
|---|---|
| GC-01 | 喂食小鱼干（dried_fish）：hunger 80→95，mood 60→68，intimacy 50→58，exp 0→15 |
| GC-02 | 喂食溢出封顶：hunger 90 + 便当(+40) → 100（不超上限） |
| GC-03 | 闲置 24h 衰减：hunger 80-48=32，mood 60-36=24，intimacy 50-12=38 |
| GC-04 | 闲置 24h 后喂食蛋糕：先衰减再增益（hunger 32+20=52） |
| GC-05 | 升级：exp 95 + 小鱼干(+15)=110 ≥ 100 → level 2，exp 10 |
| GC-06 | 连升验证（按 foods.json：便当 expGain=10）：exp 0 + 三次喂食便当（各+10）= 30 → 仍 Lv1；注入 exp=250 → 连升两级至 Lv3/exp0；注入 exp=600 → Lv4/exp150（QA 严过关修正：原描述「+30」易误读为单次增益） |
| GC-07 | 满级 99：经验封顶为 expToNextLevel(99)-1 |
| DC-01 | 精确匹配优先：「你好」命中 greet_hello（priority 100，exact） |
| DC-02 | 优先级降序：「你好可爱」同时命中 greet_hello(100,partial) 与 praise(90,partial) → 取 greet_hello |
| DC-03 | 同优先级 exact > partial：构造输入验证 |
| DC-04 | 未命中 → fallback 随机；空输入 → tapReactions 随机 |
| DC-05 | 随机选择可注入（固定种子/固定索引），双端同序列同结果 |
| RP-01 | 新配置（nextTriggerAt=null）：next = from + interval |
| RP-02 | 已排程且未来：next = 原 nextTriggerAt（若 > from） |
| RP-03 | 已过期：顺延到未来第一个整周期（base += interval 直到 > from） |
| RP-04 | enabled=false → 返回 0（无效） |

## 运行

- Android：`cd android && ./gradlew test`（`app/src/test/java/.../domain/`）
- iOS（Mac）：`cd ios/PetApp && xcodegen && open PetApp.xcodeproj` → 运行 `PetAppTests`
  （或 `xcodebuild test -scheme PetApp -destination 'platform=iOS Simulator,name=...'`）

> iOS 端在本仓库交付形态为「代码交付」，镜像测试需在 Mac 环境执行验证。
