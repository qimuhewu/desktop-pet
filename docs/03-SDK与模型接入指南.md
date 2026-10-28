# Cubism SDK 与模型接入指南

- 文档版本：v1.0
- 适用范围：Android（Windows 构建）与 iOS（Mac 构建）
- 关联：`docs/02-架构设计.md` 决策 D4、`scripts/`

---

## 1. 为什么 SDK 与模型不入库

Live2D Cubism SDK 的再分发受许可条款限制，官方免费示例模型（Hiyori 等）仅限非商业用途且禁止再分发。因此本仓库采取「**脚本放置 + 接入文档 + 缺失兜底**」三件套：

1. SDK / 模型由使用者自行从官网下载，脚本校验并放置到 `third_party/`（已被 `.gitignore` 忽略）；
2. 未放置 SDK / 模型时，App 自动使用 `FallbackPetRenderer`（Canvas / SwiftUI 程序绘制的小动物），**业务功能完全可用、绝不崩溃**；
3. 放置后构建系统自动探测并启用 Live2D 渲染路径（Android 源集隔离，iOS `#if canImport` 编译开关）。

## 2. 下载地址

| 资源 | 下载入口 |
|---|---|
| Cubism SDK for Android（Native） | Live2D 官网 → Download → Cubism SDK for Android |
| Cubism SDK for iOS（Native） | Live2D 官网 → Download → Cubism SDK for iOS |
| 示例模型 Hiyori | Live2D 官网 → Sample Data → Hiyori（注意许可条款） |

> 许可条款以官网当日版本为准。发布上架前请逐条核对：免费素材许可证（Free Material License）适用于非商业用途；商业发布需购买/更换可商用模型。

## 3. 放置脚本用法

### 3.1 Android（Windows PowerShell / Git Bash）

```powershell
# 参数为 SDK 压缩包解压后的目录（内含 CubismCore.aar 与 Framework 源码目录）
powershell -File scripts/setup-cubism-android.ps1 "C:\Downloads\CubismSdkForAndroid-5-r1"

# Unix（macOS/Linux/Git Bash）
bash scripts/setup-cubism-android.sh ~/Downloads/CubismSdkForAndroid-5-r1
```

脚本会：
1. 校验源目录内存在 Cubism Core 产物（`*.aar` 或 `*.so`，**宽松校验**：实包目录结构可能随 SDK 版本变化，脚本仅做存在性检查并交互确认）；
2. 复制到 `third_party/cubism-android/`；
3. 打印下一步指引（Gradle 会自动探测该目录并切换到 Live2D 源集）。

> ⚠️ 实包到位后，请按实际目录结构固化 `android/app/build.gradle.kts` 中 `third_party/cubism-android` 的探测逻辑（当前为「递归查找任意 .aar」的宽松实现）。

### 3.2 iOS（仅 Mac，用户后续执行）

```bash
bash scripts/setup-cubism-ios.sh ~/Downloads/CubismSdkForiOS-5-r1
```

放置后 iOS 端 `Live2DRenderer.swift` 中的 `#if canImport(Live2DCubismCore)` 分支生效。

### 3.3 示例模型 Hiyori

```powershell
powershell -File scripts/place-hiyori-model.ps1 "C:\Downloads\hiyori_free"
# 或
bash scripts/place-hiyori-model.sh ~/Downloads/hiyori_free
```

模型放置到 `third_party/models/hiyori/`。模型目录必须符合标准 Cubism 结构：根目录存在 `<name>.model3.json`，同目录含 `.moc3`、贴图、动作文件。

## 4. 模型资产运行时目录约定

| 平台 | 目录 | 说明 |
|---|---|---|
| Android | App 私有目录 `files/models/active/` | 首次启动时由 App 从 `third_party` 打包产物或用户导入目录复制；`ModelAssetManager` 解析 |
| iOS | `Application Support/PetModels/active/` | 同上 |

`ModelAssetManager.resolveActiveModelDir()` 返回 `null`（无模型或校验失败）→ 一律走兜底渲染，业务代码不得因缺模型崩溃。

## 5. 各 ROM 保活设置指引（Android）

悬浮窗 Service 可能被国产 ROM 杀后台。`PermissionGuideScreen` 内置各 ROM 的设置跳转：

| ROM | 指引 |
|---|---|
| MIUI / HyperOS（小米） | 设置 → 应用设置 → 应用管理 → 桌宠 → 自启动 + 省电策略「无限制」 |
| ColorOS（OPPO/一加） | 设置 → 电池 → 更多 → 应用速冻关闭；应用 → 桌宠 → 允许自启动/关联启动 |
| EMUI / HarmonyOS（华为） | 设置 → 应用 → 应用启动管理 → 桌宠 → 手动管理（允许自启动/关联启动/后台活动） |
| OriginOS（vivo） | 设置 → 电池 → 后台耗电管理 → 桌宠 → 允许后台高耗电；i 管家 → 权限管理 → 自启动 |
| 通用 | 电池优化白名单：设置 → 应用 → 特殊访问权限 → 电池优化 → 桌宠 → 不优化（仅引导，明示利弊） |

## 6. 许可声明文案（双端「设置 → 关于」引用）

```
本应用使用的 Live2D Cubism SDK 与示例模型「Hiyori」版权归 Live2D Inc. 所有。
示例模型的使用遵循 Live2D 免费素材许可证（Free Material License Agreement），
仅限非商业用途。如需商业使用，请替换为已获授权的模型资产。
Live2D 及 Cubism 为 Live2D Inc. 的商标。
```
