# 模型资产放置约定（占位说明，实际模型不入库）

本目录中的 `.gitignore` 规则忽略了除本 README 外的所有文件。

## 运行时模型目录

**本目录不是运行时加载目录。** App 运行时从私有目录读取模型：

- Android：`context.filesDir / models / active/`
- iOS：`Application Support / PetModels / active/`

模型必须符合标准 Cubism 结构：

```
<model_root>/
├── <name>.model3.json     # 模型描述文件（入口）
├── <name>.moc3            # 模型本体
├── <name>.physics3.json   # 物理演算（可选）
├── *.png / *.texture/     # 贴图
└── *.motion3.json         # 动作文件（可选）
```

## 放置方式

1. **脚本放置（开发机）**：`scripts/place-hiyori-model.ps1 <模型目录>` → `third_party/models/hiyori/`，
   之后由构建/开发流程复制到设备；或
2. **App 内导入（用户）**：`ModelAssetManager.importModel()` 校验后写入 `active/` 目录。

`ModelAssetManager.resolveActiveModelDir()` 返回 null 时一律走 `FallbackPetRenderer` 程序绘制兜底，业务代码不得因缺模型崩溃（架构文档 §8 共享知识 #6）。
