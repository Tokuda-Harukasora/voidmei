# ============================================
# VoidMei 便携版打包脚本
# 生成包含 JRE 8 的完整便携版 zip
# ============================================

param(
    [string]$OutputDir = "$env:USERPROFILE\Desktop",
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  VoidMei 便携版打包工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 确定版本号
if (-not $Version) {
    if (Test-Path "$ProjectDir\VoidMei.jar") {
        $jarInfo = jar -tf "$ProjectDir\VoidMei.jar" 2>$null | Select-Object -First 1
    }
    $Version = "portable"
}
Write-Host "[*] 版本: $Version" -ForegroundColor Yellow

# JRE 路径
$jrePath = "$ProjectDir\jre"
$needJreDownload = $false

if (-not (Test-Path "$jrePath\bin\java.exe")) {
    Write-Host "[!] JRE 未找到，将不包含在 zip 中" -ForegroundColor Yellow
    Write-Host "[*] 用户需运行 setup_jre.bat 下载 JRE" -ForegroundColor Yellow
    $needJreDownload = $true
} else {
    Write-Host "[OK] JRE 已就绪" -ForegroundColor Green
}

# 输出文件名
$zipName = "VoidMei_便携版.zip"
$zipPath = "$OutputDir\$zipName"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

# 创建临时打包目录
$tempDir = "$env:TEMP\voidmei_package"
if (Test-Path $tempDir) { Remove-Item -Recurse -Force $tempDir }
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

Write-Host "[*] 正在复制文件..." -ForegroundColor Yellow

# 要包含的文件和目录
$includeItems = @(
    "VoidMei.exe",
    "VoidMei.jar",
    "VoidMei.bat",
    "setup_jre.bat",
    "MANIFEST.MF",
    "LICENSE",
    "README.md",
    "ReadMe.txt",
    "ui_layout.cfg",
    "ui_layout.user.cfg",
    "使用说明.txt",
    "快速使用说明.txt",
    "更新日志.txt",
    "计算说明.md",
    "config",
    "data",
    "dep",
    "fonts",
    "image",
    "lang",
    "records",
    "voice",
    "src"
)

foreach ($item in $includeItems) {
    $srcPath = "$ProjectDir\$item"
    if (Test-Path $srcPath) {
        Copy-Item -Recurse $srcPath "$tempDir\$item" -Force
        Write-Host "  + $item" -ForegroundColor Gray
    } else {
        Write-Host "  - $item (未找到，跳过)" -ForegroundColor DarkGray
    }
}

# 复制 JRE（如果存在）
if (-not $needJreDownload) {
    Write-Host "[*] 正在复制 JRE（可能需要一些时间）..." -ForegroundColor Yellow
    Copy-Item -Recurse $jrePath "$tempDir\jre" -Force
    Write-Host "  + jre" -ForegroundColor Gray
}

Write-Host "[*] 正在压缩..." -ForegroundColor Yellow
Compress-Archive -Path "$tempDir\*" -DestinationPath $zipPath -CompressionLevel Optimal -Force

# 清理
Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue

$zipSize = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  打包完成！" -ForegroundColor Green
Write-Host "  📦 $zipPath" -ForegroundColor White
Write-Host "  📏 $zipSize MB" -ForegroundColor White
if ($needJreDownload) {
    Write-Host "  ⚠️  不含 JRE，用户需运行 setup_jre.bat" -ForegroundColor Yellow
}
Write-Host "========================================" -ForegroundColor Green
