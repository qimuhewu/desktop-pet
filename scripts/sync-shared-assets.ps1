# ============================================================
# 共享数据文件同步脚本（Windows PowerShell）
# 将 shared/assets/*.json 复制到 Android assets 与 iOS Resources
# 用法: powershell -File scripts/sync-shared-assets.ps1
# ============================================================
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sharedAssets = Join-Path $repoRoot "shared\assets"
$androidAssets = Join-Path $repoRoot "android\app\src\main\assets"
$iosResources = Join-Path $repoRoot "ios\PetApp\Resources"

function Write-Step([string]$msg) { Write-Host "[sync-shared-assets] $msg" }

$files = @("growth_rules.json", "foods.json", "dialogue_zh.json", "reminder_templates.json")

foreach ($f in $files) {
    $src = Join-Path $sharedAssets $f
    if (-not (Test-Path $src)) {
        Write-Step "错误：缺少共享文件 $src"
        exit 1
    }
    Copy-Item -Path $src -Destination (Join-Path $androidAssets $f) -Force
    New-Item -ItemType Directory -Path $iosResources -Force | Out-Null
    Copy-Item -Path $src -Destination (Join-Path $iosResources $f) -Force
    Write-Step "已同步: $f -> android assets / ios Resources"
}

Write-Step "完成！双端加载同一份共享数据文件。"
exit 0
