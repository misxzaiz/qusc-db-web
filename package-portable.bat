@echo off
chcp 65001 >nul
title QUSC-DB 绿色版打包工具

echo ========================================
echo     QUSC-DB 绿色版打包工具
echo ========================================
echo.

REM 设置变量
set "PROJECT_DIR=%CD%"
set "DIST_DIR=%PROJECT_DIR%\QUSC-DB-Portable"
set "VERSION=1.0.0"
set "BUILD_TIME=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "BUILD_TIME=%BUILD_TIME: =0%"

echo [INFO] 打包时间：%BUILD_TIME%
echo [INFO] 目标目录：%DIST_DIR%
echo.

REM 先运行构建
echo [STEP 1/3] 构建项目...
call build.bat
if %errorlevel% neq 0 (
    echo [ERROR] 项目构建失败！
    pause
    exit /b 1
)

REM 创建绿色版目录结构
echo [STEP 2/3] 创建目录结构...
if exist "%DIST_DIR%" (
    rmdir /s /q "%DIST_DIR%"
)

mkdir "%DIST_DIR%"
mkdir "%DIST_DIR%\app"
mkdir "%DIST_DIR%\docs"
mkdir "%DIST_DIR%\config"

REM 复制应用程序文件
echo [INFO] 复制应用程序文件...
copy "dist\*" "%DIST_DIR%\app\" >nul

REM 创建配置文件
echo [INFO] 创建配置文件...
(
echo # QUSC-DB 配置文件
echo # 服务器端口
echo server.port=8080
echo.
echo # 应用程序配置
echo spring.profiles.active=dev
echo.
echo # 数据库配置^(可选^)
echo # spring.datasource.url=jdbc:mysql://localhost:3306/test
echo # spring.datasource.username=root
echo # spring.datasource.password=
echo.
echo # JVM参数^(可在start.bat中修改^)
echo # -Xms512m 初始内存
echo # -Xmx2048m 最大内存
) > "%DIST_DIR%\config\application.properties"

REM 创建启动脚本
echo [INFO] 创建启动脚本...
(
echo @echo off
echo chcp 65001 ^>nul
echo title QUSC-DB 数据库管理系统 v%VERSION%
echo.
echo echo ========================================
echo echo     QUSC-DB 数据库管理系统 v%VERSION%
echo echo ========================================
echo echo.
echo.
echo REM 切换到应用目录
echo cd /d "%%~dp0app"
echo.
echo REM 检查Java环境
echo echo [INFO] 正在检查Java环境...
echo java -version ^>nul 2^>^&1
echo if %%errorlevel%% neq 0 ^(
echo     echo [ERROR] 未检测到Java环境！
echo     echo.
echo     echo 请确保已安装Java 17或更高版本。
echo     echo 下载地址：https://www.oracle.com/java/technologies/downloads/
echo     echo.
echo     pause
echo     exit /b 1
echo ^)
echo.
echo REM 启动应用程序
echo echo [INFO] 正在启动QUSC-DB数据库管理系统...
echo echo [INFO] 启动后请访问：http://localhost:8080
echo echo.
echo.
echo java -Xms512m -Xmx2048m ^
echo      -XX:+UseG1GC ^
echo      -XX:+UseStringDeduplication ^
echo      -Dfile.encoding=UTF-8 ^
echo      -Duser.timezone=Asia/Shanghai ^
echo      -jar "db-admin-0.0.1-SNAPSHOT.jar"
echo.
echo if %%errorlevel%% neq 0 ^(
echo     echo.
echo     echo [ERROR] 应用程序异常退出！
echo     pause
echo ^)
) > "%DIST_DIR%\启动QUSC-DB.bat"

REM 创建卸载脚本
echo [INFO] 创建卸载脚本...
(
echo @echo off
echo chcp 65001 ^>nul
echo title 卸载 QUSC-DB
echo.
echo echo ========================================
echo echo     卸载 QUSC-DB 数据库管理系统
echo echo ========================================
echo echo.
echo echo [WARNING] 即将删除QUSC-DB及其所有数据！
echo echo.
echo set /p confirm=确认删除？^(输入 Y 确认^):
echo if /i "%%confirm%%" neq "Y" ^(
echo     echo 取消卸载。
echo     pause
echo     exit /b
echo ^)
echo.
echo echo [INFO] 正在删除桌面快捷方式...
echo del "%%USERPROFILE%%\Desktop\QUSC-DB.lnk" 2^>nul
echo.
echo echo [INFO] 正在删除程序目录...
echo cd /d "%%~dp0.."
echo rmdir /s /q "%%~dp0"
echo.
echo echo [INFO] 卸载完成！
echo pause
) > "%DIST_DIR%\卸载QUSC-DB.bat"

REM 创建文档
echo [STEP 3/3] 创建说明文档...

REM README.md
(
echo # QUSC-DB 数据库管理系统 v%VERSION%
echo.
echo ## 系统要求
echo - Windows 7 或更高版本
echo - Java 17 或更高版本
echo - 至少 2GB 可用内存
echo.
echo ## 快速开始
echo.
echo ### 1. 检查Java环境
echo 在命令提示符中运行：
echo ```bash
echo java -version
echo ```
echo 确保版本为 17 或更高。
echo.
echo ### 2. 启动程序
echo 双击 `启动QUSC-DB.bat` 文件即可启动程序。
echo.
echo ### 3. 访问系统
echo 程序启动后，在浏览器中访问：
echo [http://localhost:8080](http://localhost:8080)
echo.
echo ## 配置说明
echo.
echo ### 修改端口
echo 编辑 `config/application.properties` 文件，修改 `server.port` 的值。
echo.
echo ### 修改内存
echo 编辑 `启动QUSC-DB.bat` 文件，修改 JVM 参数：
echo - `-Xms512m`：初始内存
echo - `-Xmx2048m`：最大内存
echo.
echo ## 常见问题
echo.
echo ### Q: 提示端口被占用
echo **A:** 关闭占用8080端口的程序，或修改配置文件中的端口号。
echo.
echo ### Q: 程序启动失败
echo **A:** 检查Java版本是否为17或更高，查看错误信息确定原因。
echo.
echo ### Q: 内存不足
echo **A:** 修改启动脚本中的JVM内存参数，根据实际内存调整。
echo.
echo ## 技术支持
echo.
echo - 构建时间：%BUILD_TIME%
echo - 版本：%VERSION%
echo - 邮箱：support@qusc.com
) > "%DIST_DIR%\docs\README.md"

REM 使用说明.txt
(
echo QUSC-DB 数据库管理系统使用说明
echo =====================================
echo.
echo 【启动方法】
echo 1. 双击"启动QUSC-DB.bat"
echo 2. 等待程序启动完成
echo 3. 打开浏览器访问：http://localhost:8080
echo.
echo 【注意事项】
echo - 首次启动可能需要较长时间，请耐心等待
echo - 确保已安装Java 17或更高版本
echo - 不要删除程序目录中的文件
echo.
echo 【配置修改】
echo - 端口配置：config\application.properties
echo - 内存配置：启动QUSC-DB.bat
echo.
echo 【卸载方法】
echo 双击"卸载QUSC-DB.bat"即可完全卸载
echo.
echo 【技术支持】
echo 如遇问题，请联系技术支持
echo 邮箱：support@qusc.com
echo.
echo 构建时间：%BUILD_TIME%
echo 版本：v%VERSION%
) > "%DIST_DIR%\docs\使用说明.txt"

REM 创建版本信息
(
echo 版本：%VERSION%
echo 构建时间：%BUILD_TIME%
echo 构建者：%USERNAME%
echo 操作系统：%OS%
echo.
echo 文件清单：
echo dir /b "%DIST_DIR%\app"
echo.
echo MD5校验值：
echo certutil -hashfile "%DIST_DIR%\app\db-admin-0.0.1-SNAPSHOT.jar" MD5
) > "%DIST_DIR%\docs\版本信息.txt" 2>&1

REM 创建桌面快捷方式脚本
echo [INFO] 创建桌面快捷方式...
(
echo Set WshShell = CreateObject^("WScript.Shell"^)
echo strDesktop = WshShell.SpecialFolders^("Desktop"^)
echo set oShellLink = WshShell.CreateShortcut^(strDesktop ^& "\QUSC-DB.lnk"^)
echo oShellLink.TargetPath = "%DIST_DIR%\启动QUSC-DB.bat"
echo oShellLink.WorkingDirectory = "%DIST_DIR%"
echo oShellLink.Description = "QUSC-DB数据库管理系统"
echo oShellLink.Save
) > "%DIST_DIR%\创建桌面快捷方式.vbs"

REM 计算总大小
echo [INFO] 计算包大小...
for /f "tokens=3" %%i in ('dir "%DIST_DIR%" /s ^| findstr "个文件"') do set "total_size=%%i"

echo.
echo ========================================
echo     绿色版打包完成！
echo ========================================
echo.
echo [INFO] 打包目录：%DIST_DIR%
echo [INFO] 版本：v%VERSION%
echo [INFO] 大小：%total_size%
echo.
echo [INFO] 目录结构：
tree "%DIST_DIR%" /F
echo.
echo [INFO] 您可以将整个QUSC-DB-Portable目录：
echo 1. 压缩成ZIP文件分发
echo 2. 复制到U盘或其他设备
echo 3. 直接在Windows电脑上运行
echo.
echo 按任意键退出...
pause >nul