#!/usr/bin/env bash
# ============================================================
# Cubism SDK for iOS 放置脚本（仅 Mac，用户后续执行）
# 用法: bash scripts/setup-cubism-ios.sh <SDK解压目录>
# ============================================================
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "[setup-cubism-ios] 用法: $0 <SDK解压目录>"
  exit 1
fi

SOURCE_DIR="$1"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_ROOT="$REPO_ROOT/third_party/cubism-ios"

log() { echo "[setup-cubism-ios] $1"; }

if [ "$(uname)" != "Darwin" ]; then
  log "警告：当前非 macOS 环境，脚本仍可执行（仅复制文件），但 iOS 构建需在 Mac 上进行。"
fi

# 1. 源目录校验
if [ ! -d "$SOURCE_DIR" ]; then
  log "错误：源目录不存在: $SOURCE_DIR"
  exit 1
fi

# 2. 宽松校验：递归查找 Live2DCubismCore framework / 动态库
CORE_FW="$(find "$SOURCE_DIR" -name 'Live2DCubismCore.framework' -maxdepth 6 2>/dev/null | head -n 1 || true)"
CORE_DYLIB="$(find "$SOURCE_DIR" -name 'libLive2DCubismCore*' 2>/dev/null | head -n 1 || true)"
if [ -z "$CORE_FW" ] && [ -z "$CORE_DYLIB" ]; then
  log "错误：未找到 Live2DCubismCore.framework 或 libLive2DCubismCore*。"
  log "请确认这是 Cubism SDK for iOS（Native）的解压目录。"
  exit 1
fi
log "找到 Core 产物: ${CORE_FW:-$CORE_DYLIB}"

# 3. 复制到 third_party/cubism-ios/
rm -rf "$DEST_ROOT"
mkdir -p "$DEST_ROOT"
cp -R "$SOURCE_DIR"/. "$DEST_ROOT"/
log "已复制 SDK 到: $DEST_ROOT"

# 4. 完成提示
log "完成！在 ios/PetApp/ 下执行 'xcodegen' 生成工程时，"
log "project.yml 会自动探测 third_party/cubism-ios 并链接 Live2D 相关 framework。"
exit 0
