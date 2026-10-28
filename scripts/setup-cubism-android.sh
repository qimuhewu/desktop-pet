#!/usr/bin/env bash
# ============================================================
# Cubism SDK for Android 放置脚本（Unix/macOS/Git Bash）
# 用法: bash scripts/setup-cubism-android.sh <SDK解压目录>
# 说明: 宽松校验——实包目录结构可能随 SDK 版本变化，实包到位后请固化探测逻辑。
# ============================================================
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "[setup-cubism-android] 用法: $0 <SDK解压目录>"
  exit 1
fi

SOURCE_DIR="$1"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_ROOT="$REPO_ROOT/third_party/cubism-android"

log() { echo "[setup-cubism-android] $1"; }

# 1. 源目录校验
if [ ! -d "$SOURCE_DIR" ]; then
  log "错误：源目录不存在: $SOURCE_DIR"
  exit 1
fi

# 2. 宽松校验：递归查找 Cubism Core 产物（.aar 或 .so）
CORE_AAR="$(find "$SOURCE_DIR" -name '*.aar' 2>/dev/null | head -n 1 || true)"
CORE_SO="$(find "$SOURCE_DIR" -name '*.so' 2>/dev/null | head -n 1 || true)"
if [ -z "$CORE_AAR" ] && [ -z "$CORE_SO" ]; then
  log "错误：未在源目录中找到 Cubism Core 产物（*.aar 或 *.so）。"
  log "请确认这是 Cubism SDK for Android（Native）的解压目录。"
  exit 1
fi
log "找到 Core 产物: ${CORE_AAR:-$CORE_SO}"

# 3. 复制到 third_party/cubism-android/
rm -rf "$DEST_ROOT"
mkdir -p "$DEST_ROOT"
cp -R "$SOURCE_DIR"/. "$DEST_ROOT"/
log "已复制 SDK 到: $DEST_ROOT"

# 4. 完成提示
log "完成！Android 构建时会自动探测 third_party/cubism-android 并启用 Live2D 渲染路径。"
log "下一步：bash scripts/place-hiyori-model.sh <模型目录> 放置示例模型（可选）。"
exit 0
