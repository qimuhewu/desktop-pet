#!/usr/bin/env bash
# ============================================================
# 共享数据文件同步脚本（Unix/macOS/Git Bash，与 .ps1 伴随）
# 用法: bash scripts/sync-shared-assets.sh
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHARED_ASSETS="$REPO_ROOT/shared/assets"
ANDROID_ASSETS="$REPO_ROOT/android/app/src/main/assets"
IOS_RESOURCES="$REPO_ROOT/ios/PetApp/Resources"

log() { echo "[sync-shared-assets] $1"; }

FILES=(growth_rules.json foods.json dialogue_zh.json reminder_templates.json)

mkdir -p "$ANDROID_ASSETS" "$IOS_RESOURCES"
for f in "${FILES[@]}"; do
  if [ ! -f "$SHARED_ASSETS/$f" ]; then
    log "错误：缺少共享文件 $SHARED_ASSETS/$f"
    exit 1
  fi
  cp -f "$SHARED_ASSETS/$f" "$ANDROID_ASSETS/$f"
  cp -f "$SHARED_ASSETS/$f" "$IOS_RESOURCES/$f"
  log "已同步: $f -> android assets / ios Resources"
done

log "完成！双端加载同一份共享数据文件。"
exit 0
