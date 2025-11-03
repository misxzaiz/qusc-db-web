@echo off
chcp 65001 >nul
title QUSC-DB 安装包制作工具

echo ========================================
echo     QUSC-DB 安装包制作工具
echo ========================================
echo.

REM 检查NSIS是否已安装
echo [INFO] 检查NSIS环境...
makensis.exe /VERSION >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未检测到NSIS！
    echo.
    echo 请先安装NSIS (Nullsoft Scriptable Install System)
    echo 下载地址：http://nsis.sourceforge.net/Download
    echo.
    echo 安装后请确保将makensis.exe添加到系统PATH环境变量
    echo.
    pause
    exit /b 1
)

echo [INFO] NSIS已安装
echo.

REM 先运行构建脚本
echo [INFO] 正在构建项目...
call build.bat
if %errorlevel% neq 0 (
    echo [ERROR] 项目构建失败！
    pause
    exit /b 1
)

REM 创建NSIS脚本
echo [INFO] 创建安装包脚本...
(
echo ; QUSC-DB 数据库管理系统安装脚本
echo ; 生成时间：%date% %time%
echo.
echo !define APPNAME "QUSC-DB"
echo !define APPVERSION "1.0.0"
echo !define PUBLISHER "QUSC Team"
echo !define DESCRIPTION "QUSC数据库管理系统"
echo !define URL "http://localhost:8080"
echo.
echo !define INSTALL_DIR "$PROGRAMFILES\${APPNAME}"
echo !define STARTMENU_DIR "$SMPROGRAMS\${APPNAME}"
echo.
echo ; 包含现代UI
echo !include "MUI2.nsh"
echo.
echo ; 通用设置
echo Name "${APPNAME}"
echo OutFile "QUSC-DB-Setup-${APPVERSION}.exe"
echo InstallDir "${INSTALL_DIR}"
echo InstallDirRegKey HKLM "Software\${APPNAME}" "InstallPath"
echo RequestExecutionLevel admin
echo.
echo ; 界面设置
echo !define MUI_ABORTWARNING
echo !define MUI_ICON "icon.ico" ;^&rem ; 如果有图标文件
echo !define MUI_UNICON "icon.ico" ;^&rem ; 如果有图标文件
echo.
echo ; 安装页面
echo !insertmacro MUI_PAGE_WELCOME
echo !insertmacro MUI_PAGE_LICENSE "LICENSE.txt"
echo !insertmacro MUI_PAGE_COMPONENTS
echo !insertmacro MUI_PAGE_DIRECTORY
echo !insertmacro MUI_PAGE_INSTFILES
echo !insertmacro MUI_PAGE_FINISH
echo.
echo ; 卸载页面
echo !insertmacro MUI_UNPAGE_WELCOME
echo !insertmacro MUI_UNPAGE_CONFIRM
echo !insertmacro MUI_UNPAGE_INSTFILES
echo !insertmacro MUI_UNPAGE_FINISH
echo.
echo ; 语言
echo !insertmacro MUI_LANGUAGE "SimpChinese"
echo !insertmacro MUI_LANGUAGE "English"
echo.
echo ; 安装组件
echo Section "主程序" SecMain
echo     SectionIn RO
echo.
echo     SetOutPath "$INSTDIR"
echo     File /r "dist\*.*"
echo.
echo     ; 创建开始菜单快捷方式
echo     CreateDirectory "${STARTMENU_DIR}"
echo     CreateShortCut "${STARTMENU_DIR}\${APPNAME}.lnk" "$INSTDIR\start.bat"
echo     CreateShortCut "${STARTMENU_DIR}\卸载${APPNAME}.lnk" "$INSTDIR\Uninstall.exe"
echo.
echo     ; 创建桌面快捷方式
echo     CreateShortCut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\start.bat"
echo.
echo     ; 注册表项
echo     WriteRegStr HKLM "Software\${APPNAME}" "InstallPath" "$INSTDIR"
echo     WriteRegStr HKLM "Software\${APPNAME}" "Version" "${APPVERSION}"
echo.
echo     ; 卸载信息
echo     WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayName" "${APPNAME}"
echo     WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "UninstallString" "$INSTDIR\Uninstall.exe"
echo     WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayVersion" "${APPVERSION}"
echo     WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "Publisher" "${PUBLISHER}"
echo     WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "URLInfoAbout" "${URL}"
echo     WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "NoModify" 1
echo     WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "NoRepair" 1
echo.
echo     ; 创建卸载程序
echo     WriteUninstaller "$INSTDIR\Uninstall.exe"
echo SectionEnd
echo.
echo ; 卸载程序
echo Section "Uninstall"
echo     ; 删除文件
echo     RMDir /r "$INSTDIR"
echo.
echo     ; 删除快捷方式
echo     Delete "${STARTMENU_DIR}\${APPNAME}.lnk"
echo     Delete "${STARTMENU_DIR}\卸载${APPNAME}.lnk"
echo     RMDir "${STARTMENU_DIR}"
echo     Delete "$DESKTOP\${APPNAME}.lnk"
echo.
echo     ; 删除注册表
echo     DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}"
echo     DeleteRegKey HKLM "Software\${APPNAME}"
echo SectionEnd
) > "installer.nsi"

REM 创建许可证文件
echo [INFO] 创建许可证文件...
echo QUSC-DB 数据库管理系统许可证 > LICENSE.txt
echo. >> LICENSE.txt
echo 版权所有 ^(C^) 2024 QUSC Team >> LICENSE.txt
echo. >> LICENSE.txt
echo 本软件为免费软件，您可以自由使用、分发和修改。 >> LICENSE.txt
echo. >> LICENSE.txt
echo 免责声明： >> LICENSE.txt
echo 本软件按"原样"提供，不提供任何明示或暗示的担保。 >> LICENSE.txt
echo 作者不承担使用本软件所造成的任何损失责任。 >> LICENSE.txt

REM 编译安装包
echo [INFO] 正在生成安装包...
makensis.exe installer.nsi
if %errorlevel% neq 0 (
    echo [ERROR] 安装包生成失败！
    pause
    exit /b 1
)

REM 清理临时文件
del installer.nsi
del LICENSE.txt

echo.
echo ========================================
echo     安装包制作完成！
echo ========================================
echo.
echo [INFO] 安装包文件：QUSC-DB-Setup-%APPVERSION%.exe
echo [INFO] 您可以将此安装包分发给用户安装
echo.
echo 按任意键退出...
pause >nul