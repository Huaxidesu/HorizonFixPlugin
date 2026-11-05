@echo off
chcp 936 >nul
rem 构建 HorizonFixPlugin。
rem 首次构建从 GTNH 官方 Release 下载 Thermos 服务端 jar，抽出 Bukkit API 供编译期使用；
rem 下载或校验失败时退回公共仓库解析 spigot-api。

setlocal
set HERE=%~dp0
cd /d "%HERE%"

set API_JAR=%HERE%libs\bukkit-api-1.7.10.jar

if exist "%API_JAR%" goto build
echo [1/2] 获取 Bukkit API
call :fetch_api

:build
echo [2/2] 编译打包
call mvn -B clean package
if errorlevel 1 goto fail

echo.
echo 构建完成：%HERE%target\HorizonFixPlugin-1.2.2.jar
echo 安装位置：gtnh1\server\plugins\
goto end

:fetch_api
set URL=https://github.com/GTNewHorizons/Thermos/releases/download/1.0-GTNH/Thermos-1.7.10-1614.UNOFFICIAL-server.jar
set EXPECTED=b02e4c9d6cd2df87aac34f314c35fb1d5995e72c
set JAR_TOOL=jar
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jar.exe" set JAR_TOOL=%JAVA_HOME%\bin\jar.exe

if exist "%HERE%tmp\api" rmdir /s /q "%HERE%tmp\api"
mkdir "%HERE%tmp\api"
if not exist "%HERE%libs" mkdir "%HERE%libs"

curl -L --fail --max-time 300 -o "%HERE%tmp\thermos-server.jar" "%URL%"
if errorlevel 1 (
    echo [警告] 下载失败，改用公共仓库解析依赖
    goto fetch_done
)

set ACTUAL=
for /f "skip=1 delims=" %%H in ('certutil -hashfile "%HERE%tmp\thermos-server.jar" SHA1') do if not defined ACTUAL set "ACTUAL=%%H"
set ACTUAL=%ACTUAL: =%
if /i not "%ACTUAL%"=="%EXPECTED%" (
    echo [警告] SHA-1 校验不匹配，改用公共仓库解析依赖
    goto fetch_done
)

pushd "%HERE%tmp\api"
"%JAR_TOOL%" xf "%HERE%tmp\thermos-server.jar" org/bukkit
"%JAR_TOOL%" cf "%API_JAR%" org
popd
echo 已提取 API：%API_JAR%

:fetch_done
if exist "%HERE%tmp\api" rmdir /s /q "%HERE%tmp\api"
del /q "%HERE%tmp\thermos-server.jar" 2>nul
exit /b 0

:fail
echo.
echo [错误] 构建失败，产物未生成

:end
endlocal
pause
