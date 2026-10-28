#!/usr/bin/env bash
# ============================================================
# Hiyori 示例模型放置脚本（Unix/macOS/Git Bash）
# 用法: bash scripts/place-hiyori-model.sh <模型目录>
# ============================================================
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "[place-hiyori-model] 用法: $0 <模型目录>"
  exit 1
fi

SOURCE_DIR="$1"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_ROOT="$REPO_ROOT/third_party/models/hiyori"

log() { echo "[place-hiyori-model] $1"; }

if [ ! -d "$SOURCE_DIR" ]; then
  log "错误：源目录不存在: $SOURCE_DIR"
  exit 1
fi

# 2. 宽松校验：根目录（或一级子目录）须有 *.model3.json
MODEL3="$(find "$SOURCE_DIR" -maxdepth 2 -name '*.model3.json' 2>/dev/null | head -n 1 || true)"
if [ -z "$MODEL3" ]; then
  log "错误：未找到 *.model3.json，请确认这是标准 Cubism 模型目录。"
  exit 1
fi
log "找到模型描述文件: $MODEL3"

# 3. 复制模型根目录
MODEL_ROOT="$(cd "$(dirname "$MODEL3")" && pwd)"
rm -rf "$DEST_ROOT"
mkdir -p "$(dirname "$DEST_ROOT")"
cp -R "$MODEL_ROOT" "$DEST_ROOT"
log "已复制模型到: $DEST_ROOT"

# 4. 许可提示
log "注意：Hiyori 为 Live2D 官方免费示例模型，仅限非商业用途，请阅读 docs/03-SDK与模型接入指南.md §6。"
exit 0
