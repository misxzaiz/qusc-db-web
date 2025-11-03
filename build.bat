@echo off
chcp 65001 >nul
title QUSC-DB 构建打包工具

echo ========================================
echo     QUSC-DB 构建打包工具
echo ========================================
echo.

REM 设置变量
set "PROJECT_DIR=%CD%"
set "FRONTEND_DIR=%PROJECT_DIR%\frontend"
set "BACKEND_DIR=%PROJECT_DIR%\backend"
set "DIST_DIR=%PROJECT_DIR%\dist"
set "JAR_FILE=%BACKEND_DIR%\target\db-admin-0.0.1-SNAPSHOT.jar"

echo [INFO] 项目目录：%PROJECT_DIR%
echo [INFO] 前端目录：%FRONTEND_DIR%
echo [INFO] 后端目录：%BACKEND_DIR%
echo.

REM 检查Node.js环境
echo [STEP 1/4] 检查环境依赖...
echo [INFO] 检查Node.js环境...
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未检测到Node.js环境！
    echo.
    echo 请确保已安装Node.js 16或更高版本。
    echo 下载地址：https://nodejs.org/
    echo.
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('node --version') do echo [INFO] Node.js版本：%%i

REM 检查npm环境
npm --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未检测到npm环境！
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('npm --version') do echo [INFO] npm版本：%%i

REM 检查Java环境
echo [INFO] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未检测到Java环境！
    echo.
    echo 请确保已安装Java 17或更高版本。
    echo 下载地址：https://www.oracle.com/java/technologies/downloads/
    echo.
    pause
    exit /b 1
)

REM 检查Maven环境
echo [INFO] 检查Maven环境...
call mvn --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] 未检测到Maven环境，将使用项目内置的Maven Wrapper...
    if not exist "%BACKEND_DIR%\mvnw" (
        echo [ERROR] 未找到Maven Wrapper！
        echo.
        echo 请确保已安装Maven或项目包含Maven Wrapper。
        echo.
        pause
        exit /b 1
    )
    set "MAVEN_CMD=call mvnw"
) else (
    set "MAVEN_CMD=call mvn"
)

echo [INFO] 环境检查完成！
echo.

REM 清理旧的构建文件
echo [STEP 2/4] 清理旧文件...
if exist "%DIST_DIR%" (
    echo [INFO] 清理旧的分发目录...
    rmdir /s /q "%DIST_DIR%"
)
if exist "%JAR_FILE%" (
    echo [INFO] 清理旧的JAR文件...
    del /q "%JAR_FILE%"
)
echo [INFO] 清理完成！
echo.

REM 构建前端
echo [STEP 3/4] 构建前端项目...
cd /d "%FRONTEND_DIR%"

echo [INFO] 安装前端依赖...
npm install
if %errorlevel% neq 0 (
    echo [ERROR] 前端依赖安装失败！
    pause
    exit /b 1
)

echo [INFO] 构建前端项目...
npm run build
if %errorlevel% neq 0 (
    echo [ERROR] 前端构建失败！
    pause
    exit /b 1
)

echo [INFO] 前端构建完成！
echo.

REM 构建后端
echo [STEP 4/4] 构建后端项目...
cd /d "%BACKEND_DIR%"

echo [INFO] 清理并编译后端项目...
%MAVEN_CMD% clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] 后端构建失败！
    pause
    exit /b 1
)

if not exist "%JAR_FILE%" (
    echo [ERROR] JAR文件未生成！
    pause
    exit /b 1
)

echo [INFO] 后端构建完成！
echo.

REM 创建分发目录
echo [INFO] 创建分发包...
mkdir "%DIST_DIR%" 2>nul

REM 复制文件到分发目录
echo [INFO] 复制应用程序文件...
copy "%JAR_FILE%" "%DIST_DIR%\" >nul
copy "%PROJECT_DIR%\start.bat" "%DIST_DIR%\" >nul

REM 创建README文件
echo [INFO] 创建说明文件...
echo QUSC-DB 数据库管理系统 > "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo 安装和运行说明： >> "%DIST_DIR%\README.txt"
echo ================== >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo 1. 系统要求： >> "%DIST_DIR%\README.txt"
echo    - Java 17 或更高版本 >> "%DIST_DIR%\README.txt"
echo    - Windows 7 或更高版本 >> "%DIST_DIR%\README.txt"
echo    - 至少 2GB 可用内存 >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo 2. 启动方法： >> "%DIST_DIR%\README.txt"
echo    双击 start.bat 文件即可启动程序 >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo 3. 访问地址： >> "%DIST_DIR%\README.txt"
echo    程序启动后，在浏览器中访问：http://localhost:8080 >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo 4. 常见问题： >> "%DIST_DIR%\README.txt"
echo    - 如果提示端口被占用，请关闭占用8080端口的程序 >> "%DIST_DIR%\README.txt"
echo    - 如果提示内存不足，请调整start.bat中的JVM参数 >> "%DIST_DIR%\README.txt"
echo    - 更多帮助请访问项目主页 >> "%DIST_DIR%\README.txt"

REM 创建版本信息文件
echo [INFO] 创建版本信息...
echo 构建时间：%date% %time% > "%DIST_DIR%\VERSION.txt"
echo Git提交：%COMMIT_HASH% >> "%DIST_DIR%\VERSION.txt" 2>nul
echo 构建者：%USERNAME% >> "%DIST_DIR%\VERSION.txt"

echo.
echo ========================================
echo     构建完成！
echo ========================================
echo.
echo [INFO] 分发包已创建在：%DIST_DIR%
echo [INFO] 包含文件：
dir /b "%DIST_DIR%"
echo.
echo [INFO] 您可以将整个dist目录复制到其他计算机运行
echo.
echo 按任意键退出...
pause >nul