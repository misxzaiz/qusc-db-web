@echo off
chcp 65001 >nul
title QUSC-DB 打包工具

:menu
cls
echo ========================================
echo     QUSC-DB 数据库管理系统打包工具
echo ========================================
echo.
echo 请选择打包方式：
echo.
echo   1. 构建项目（生成到dist目录）
echo   2. 创建绿色版（免安装便携版）
echo   3. 创建安装包（需要NSIS）
echo   4. 退出
echo.
set /p choice=请输入选项 (1-4):

if "%choice%"=="1" goto build
if "%choice%"=="2" goto portable
if "%choice%"=="3" goto installer
if "%choice%"=="4" goto exit
echo [ERROR] 无效选项，请重新选择！
timeout /t 2 >nul
goto menu

:build
echo.
echo [INFO] 开始构建项目...
echo.
call build.bat
if %errorlevel% neq 0 (
    echo [ERROR] 构建失败！
    pause
    goto menu
)
echo.
echo [INFO] 构建完成！
echo 按任意键返回主菜单...
pause >nul
goto menu

:portable
echo.
echo [INFO] 开始创建绿色版...
echo.
call package-portable.bat
if %errorlevel% neq 0 (
    echo [ERROR] 创建绿色版失败！
    pause
    goto menu
)
echo.
echo [INFO] 绿色版创建完成！
echo 按任意键返回主菜单...
pause >nul
goto menu

:installer
echo.
echo [WARNING] 创建安装包需要NSIS环境！
echo [INFO] 确保已安装NSIS并添加到PATH环境变量
echo.
set /p confirm=确认继续？(Y/N):
if /i not "%confirm%"=="Y" goto menu

echo.
echo [INFO] 开始创建安装包...
echo.
call create-installer.bat
if %errorlevel% neq 0 (
    echo [ERROR] 创建安装包失败！
    pause
    goto menu
)
echo.
echo [INFO] 安装包创建完成！
echo 按任意键返回主菜单...
pause >nul
goto menu

:exit
echo.
echo [INFO] 感谢使用QUSC-DB打包工具！
echo.
pause
exit /b 0