# mobile-desktop-pet（手机版桌宠）

Android + iOS 双端 Live2D 风格虚拟宠物：Android 通过悬浮窗实现「真·桌宠」，iOS 通过灵动岛（Live Activity）+ 桌面/锁屏小组件实现「轻量桌宠」。

## 目录结构

```
mobile-desktop-pet/
├── README.md            # 本文件
├── docs/                # PRD、架构设计、接入指南、数据规范
├── shared/              # 双端共享数据文件（单一事实源）
│   ├── schema/          # 双端一致的 SQLite 表结构
│   └── assets/          # 养成规则 / 食物表 / 中文语料 / 提醒模板（JSON）
├── scripts/             # SDK/模型放置脚本、共享资产同步脚本
├── android/             # Android 端（Kotlin，Windows 命令行可构建）
└── ios/                 # iOS 端（Swift，仅代码交付，待 Mac 环境编译验证）
```

## 快速开始（Android）

1. **放置 Cubism SDK（可选，不放置则走程序绘制兜底，App 不会崩溃）**
   - 从 Live2D 官网下载 **Cubism SDK for Android**（Native）；
   - 在仓库根目录执行 `scripts/setup-cubism-android.ps1 <下载包解压目录>`；
   - 详细步骤见 `docs/03-SDK与模型接入指南.md`。
2. **放置示例模型（可选）**
   - 下载 Cubism 官方免费示例模型 Hiyori，执行 `scripts/place-hiyori-model.ps1 <模型目录>`。
3. **同步共享数据文件**（仓库内已内置同步副本，如修改 `shared/` 后需重新执行）
   - 执行 `scripts/sync-shared-assets.ps1`。
4. **构建**（需 JDK 17 + Android SDK Platform 34）：
   ```bash
   cd android
   ./gradlew assembleDebug        # 构建 APK
   ./gradlew test                 # 运行 JVM 单元测试（业务引擎镜像测试）
   ```

## 快速开始（iOS，需 Mac + Xcode 15+）

1. 安装 [XcodeGen](https://github.com/yonaskolb/XcodeGen)；
2. 执行 `scripts/setup-cubism-ios.sh <下载包解压目录>` 放置 Cubism SDK for iOS（可选）；
3. 在 `ios/PetApp/` 下执行 `xcodegen` 生成 `PetApp.xcodeproj`；
4. Xcode 打开工程，选择签名团队后运行。

> iOS 端在本仓库的交付形态为「代码交付」：编译与真机验证需 Mac + Xcode 环境。

## 许可声明（重要）

- Cubism SDK 与 Cubism 官方免费示例模型（Hiyori 等）**均不入库**（`third_party/` 已被 `.gitignore` 忽略），使用前请阅读并遵守 Live2D Cubism 的许可条款（免费示例模型仅限非商业用途等限制）。
- 首发示例模型的版权声明文案见 `docs/03-SDK与模型接入指南.md`，双端「设置 → 关于」页均已内置展示。

## 核心设计速览

| 主题 | 说明 |
|---|---|
| 渲染抽象 | `PetRenderer` 接口 + `Live2DRenderer`（Cubism）+ `FallbackPetRenderer`（程序绘制兜底），缺 SDK/模型时自动降级，不崩溃 |
| 养成一致性 | 状态衰减「读取时惰性结算」，双端加载同一份 `shared/` JSON + 同一套算法 + 镜像单元测试 |
| 数据规范 | 表结构与字段命名以 `shared/schema/pet_db_schema.sql` + `docs/04-数据模型与养成规则规范.md` 为唯一事实源 |
| 云同步预留 | `CloudSyncAdapter` 接口 + 快照 JSON 导出（snake_case），第一版仅本地 |
