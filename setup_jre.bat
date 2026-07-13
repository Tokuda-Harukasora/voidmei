@echo off
setlocal enabledelayedexpansion

REM ============================================
REM VoidMei 便携版 - JRE 8 自动下载脚本
REM 下载 Azul Zulu JRE 8 到当前目录的 jre 文件夹
REM ============================================

set JRE_DIR=%~dp0jre
set JRE_URL=https://cdn.azul.com/zulu/bin/zulu8.84.0.21-ca-jre8.0.522-win_x64.zip
set JRE_ZIP=%TEMP%\zulu8-jre.zip

echo.
echo ========================================
echo   VoidMei 便携版 - JRE 8 安装脚本
echo ========================================
echo.

REM 检查是否已存在 JRE
if exist "%JRE_DIR%\bin\java.exe" (
    echo [OK] JRE 已存在: %JRE_DIR%
    goto :done
)

echo [*] 正在下载 Azul Zulu JRE 8 (约 40MB)...
echo [*] %JRE_URL%
echo.

REM 使用 PowerShell 下载
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%JRE_URL%' -OutFile '%JRE_ZIP%' -UseBasicParsing}" 2>nul

if not exist "%JRE_ZIP%" (
    echo [ERROR] 下载失败！请检查网络连接。
    echo.
    echo 手动下载地址:
    echo https://www.azul.com/downloads/?version=java-8-lts^&os=windows^&architecture=x86-64-bit^&package=jre
    echo 解压后将 jre 文件夹放到当前目录即可。
    pause
    exit /b 1
)

echo [OK] 下载完成，正在解压...
echo.

REM 解压到临时目录
set EXTRACT_DIR=%TEMP%\zulu8-extract
if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
mkdir "%EXTRACT_DIR%"

powershell -Command "& {Expand-Archive -Path '%JRE_ZIP%' -DestinationPath '%EXTRACT_DIR%' -Force}" 2>nul

REM 查找解压后的 jre 目录并移动
for /d %%d in ("%EXTRACT_DIR%\*") do (
    if exist "%%d\bin\java.exe" (
        echo [*] 找到 JRE: %%d
        move "%%d" "%JRE_DIR%" >nul 2>&1
        goto :verify
    )
    REM 有些 Zulu 包内有 zulu8-xxx-jre 子目录
    for /d %%s in ("%%d\*") do (
        if exist "%%s\bin\java.exe" (
            echo [*] 找到 JRE: %%s
            move "%%s" "%JRE_DIR%" >nul 2>&1
            goto :verify
        )
    )
)

:verify
REM 清理临时文件
del "%JRE_ZIP%" 2>nul
rmdir /s /q "%EXTRACT_DIR%" 2>nul

if exist "%JRE_DIR%\bin\java.exe" (
    echo [OK] JRE 8 安装完成！
    echo [OK] 位置: %JRE_DIR%
) else (
    echo [ERROR] JRE 安装失败，请手动安装 Java 8。
    pause
    exit /b 1
)

:done
echo.
echo [*] 现在可以双击 VoidMei.exe 或 VoidMei.bat 启动程序。
echo.
pause
