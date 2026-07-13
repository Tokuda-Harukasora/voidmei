@echo off
setlocal

REM ============================================
REM VoidMei 便携版 - 发布到 GitHub 脚本
REM ============================================

echo ========================================
echo   VoidMei 便携版 - GitHub 发布脚本
echo ========================================
echo.

REM 检查 git
where git >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未找到 git，请先安装 Git
    pause
    exit /b 1
)

REM 获取 GitHub 用户名
set /p GITHUB_USER="请输入你的 GitHub 用户名: "
if "%GITHUB_USER%"=="" (
    echo [ERROR] 用户名不能为空
    pause
    exit /b 1
)

set REPO_URL=https://github.com/%GITHUB_USER%/voidmei-portable.git

echo.
echo [*] 将发布到: %REPO_URL%
echo [*] 协议: GPL-3.0 (继承自原始项目 matrixsukhoi/voidmei)
echo.

set /p CONFIRM="确认发布? (y/n): "
if /i not "%CONFIRM%"=="y" (
    echo 已取消
    pause
    exit /b 0
)

echo.
echo [*] 请在浏览器中创建仓库:
echo     https://github.com/new
echo     仓库名: voidmei-portable
echo     不要勾选 "Initialize this repository with a README"
echo.
pause

echo [*] 正在设置远程仓库...
git remote set-url origin %REPO_URL%

echo [*] 正在推送...
git push -u origin master

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo   发布成功！
    echo   %REPO_URL%
    echo ========================================
    echo.
    echo 下一步：创建 Release
    echo   1. 访问 %REPO_URL%/releases/new
    echo   2. Tag: v1.0-portable
    echo   3. 上传桌面的 VoidMei_便携版.zip
    echo   4. 点击 Publish release
) else (
    echo.
    echo [ERROR] 推送失败，请确认:
    echo   1. 已在 GitHub 创建 voidmei-portable 仓库
    echo   2. GitHub 已登录 (git config --global credential.helper)
    echo   3. 网络连接正常
)

pause
