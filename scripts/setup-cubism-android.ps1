# ============================================================
# Cubism SDK for Android 放置脚本（Windows PowerShell）
# 用法: powershell -File scripts/setup-cubism-android.ps1 <SDK解压目录>
# 说明: 校验为「宽松校验」——Cubism SDK 实包目录结构可能随版本变化，
#       实包到位后请按实际目录结构固化 android/app/build.gradle.kts 的探测逻辑。
# ============================================================
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDir
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$destRoot = Join-Path $repoRoot "third_party\cubism-android"

function Write-Step([string]$msg) { Write-Host "[setup-cubism-android] $msg" }

# 1. 源目录校验
if (-not (Test-Path $SourceDir)) {
    Write-Step "错误：源目录不存在: $SourceDir"
    exit 1
}

# 2. 宽松校验：递归查找 Cubism Core 产物（.aar 或 .so）
$coreAar = Get-ChildItem -Path $SourceDir -Recurse -Filter "*.aar" -ErrorAction SilentlyContinue | Select-Object -First 1
$coreSo  = Get-ChildItem -Path $SourceDir -Recurse -Include "*.so" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $coreAar -and $null -eq $coreSo) {
    Write-Step "错误：未在源目录中找到 Cubism Core 产物（*.aar 或 *.so）。"
    Write-Step "请确认这是 Cubism SDK for Android（Native）的解压目录。"
    exit 1
}
Write-Step "找到 Core 产物: $(if ($coreAar) { $coreAar.FullName } else { $coreSo.FullName })"

# 3. 复制到 third_party/cubism-android/
if (Test-Path $destRoot) {
    Write-Step "目标目录已存在，先清理: $destRoot"
    Remove-Item -Recurse -Force $destRoot
}
New-Item -ItemType Directory -Path $destRoot -Force | Out-Null
Copy-Item -Path (Join-Path $SourceDir "*") -Destination $destRoot -Recurse -Force
Write-Step "已复制 SDK 到: $destRoot"

# 4. 完成提示
Write-Step "完成！Android 构建时会自动探测 third_party/cubism-android 并启用 Live2D 渲染路径。"
Write-Step "下一步：bash scripts/place-hiyori-model.ps1 <模型目录> 放置示例模型（可选）。"
exit 0
