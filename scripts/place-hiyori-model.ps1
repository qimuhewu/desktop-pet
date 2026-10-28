# ============================================================
# Hiyori 示例模型放置脚本（Windows PowerShell）
# 用法: powershell -File scripts/place-hiyori-model.ps1 <模型目录>
# 校验: 模型目录根须存在 *.model3.json（标准 Cubism 结构，宽松校验）
# ============================================================
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDir
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$destRoot = Join-Path $repoRoot "third_party\models\hiyori"

function Write-Step([string]$msg) { Write-Host "[place-hiyori-model] $msg" }

# 1. 源目录校验
if (-not (Test-Path $SourceDir)) {
    Write-Step "错误：源目录不存在: $SourceDir"
    exit 1
}

# 2. 宽松校验：根目录（或一级子目录）须有 *.model3.json
$model3 = Get-ChildItem -Path $SourceDir -Filter "*.model3.json" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $model3) {
    $model3 = Get-ChildItem -Path $SourceDir -Directory | ForEach-Object {
        Get-ChildItem -Path $_.FullName -Filter "*.model3.json" -ErrorAction SilentlyContinue
    } | Select-Object -First 1
}
if ($null -eq $model3) {
    Write-Step "错误：未找到 *.model3.json，请确认这是标准 Cubism 模型目录。"
    exit 1
}
Write-Step "找到模型描述文件: $($model3.FullName)"

# 3. 复制模型（若 model3.json 在子目录，则复制其所在目录为模型根）
$modelRoot = $model3.DirectoryName
if (Test-Path $destRoot) {
    Write-Step "目标目录已存在，先清理: $destRoot"
    Remove-Item -Recurse -Force $destRoot
}
New-Item -ItemType Directory -Path (Split-Path -Parent $destRoot) -Force | Out-Null
Copy-Item -Path $modelRoot -Destination $destRoot -Recurse -Force
Write-Step "已复制模型到: $destRoot"

# 4. 许可提示
Write-Step "注意：Hiyori 为 Live2D 官方免费示例模型，仅限非商业用途，请阅读 docs/03-SDK与模型接入指南.md §6。"
exit 0
