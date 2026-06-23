@echo off
REM 外部 Skill 入口(Windows):转发请求给 SZU Agent 常驻 HTTP 服务。
REM 输入:  stdin 的 JSON {"name":"calendar_get","arguments":{}}
REM 可移植:daemon 地址来自环境变量 SZU_AGENT_URL(默认 http://localhost:8765)。
REM
REM 说明:用 curl 的 --data-binary @- 直接从 stdin 读取请求体,字节原样透传,
REM 避免临时文件 + PowerShell 重定向带来的 UTF-16/BOM 编码问题(中文参数会乱码)。
REM Author: 王子豪 / 2023150090
setlocal

if defined SZU_AGENT_URL (set "DAEMON_URL=%SZU_AGENT_URL%") else (set "DAEMON_URL=http://localhost:8765")

curl -s -X POST "%DAEMON_URL%/call" -H "Content-Type: application/json" --data-binary @-
