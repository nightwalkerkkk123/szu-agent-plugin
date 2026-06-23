@echo off
REM scripts\serve.bat - 启动 SZU Agent 常驻 HTTP 服务(Windows)
REM
REM 一个常驻 JVM,同时给 Skill(curl POST /call)与 MCP(JSON-RPC POST /mcp)提供能力。
REM 可移植:jar 路径相对本仓库根目录解析,不写死绝对路径;端口可经参数或环境变量覆盖。
REM
REM 用法:
REM   scripts\serve.bat                  前台启动,默认端口 8765(Ctrl-C 停止)
REM   scripts\serve.bat --port 9000      指定端口
REM   scripts\serve.bat --new-window     在独立窗口启动(相当于后台;关闭该窗口即停止)
REM   set SZU_AGENT_PORT=9000 ^&^& scripts\serve.bat
REM
REM 停止:前台用 Ctrl-C;--new-window 模式关闭那个标题为 "SZU Agent :端口" 的窗口。
REM Author: 王子豪 / 2023150090
setlocal

set "SCRIPT_DIR=%~dp0"
set "REPO_ROOT=%SCRIPT_DIR%.."
set "JAR_PATH=%REPO_ROOT%\target\szu-agent-plugin.jar"
set "NEWWIN="

if defined SZU_AGENT_PORT (set "PORT=%SZU_AGENT_PORT%") else (set "PORT=8765")

:parse
if "%~1"=="" goto run
if "%~1"=="--port" (set "PORT=%~2" & shift & shift & goto parse)
if "%~1"=="--new-window" (set "NEWWIN=1" & shift & goto parse)
echo 未知参数: %~1 1>&2
exit /b 2

:run
if not exist "%JAR_PATH%" (
    echo 找不到 jar: %JAR_PATH% 1>&2
    echo 请先构建:  mvn -q -DskipTests package 1>&2
    exit /b 1
)

if defined NEWWIN (
    start "SZU Agent :%PORT%" java -jar "%JAR_PATH%" mcp serve --http --port %PORT%
    echo 已在新窗口启动 SZU Agent 服务,端口 %PORT%(窗口标题 "SZU Agent :%PORT%")。
    echo   健康检查: curl http://localhost:%PORT%/health
    echo   停止:     关闭那个新开的窗口
) else (
    echo 前台启动 SZU Agent HTTP 服务,端口 %PORT% (Ctrl-C 停止)
    echo   健康检查: curl http://localhost:%PORT%/health
    java -jar "%JAR_PATH%" mcp serve --http --port %PORT%
)
