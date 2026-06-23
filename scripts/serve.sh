#!/usr/bin/env bash
# scripts/serve.sh — 启动 SZU Agent 常驻 HTTP 服务(daemon)
#
# 一个常驻 JVM,同时给两类调用方提供能力:
#   • Skill  → curl POST /call   (无 JVM 冷启动,毫秒级)
#   • MCP    → JSON-RPC POST /mcp (Claude Code / Desktop 经 HTTP 连入)
#
# 可移植:纯 JDK,不写死任何绝对路径——jar 路径相对本仓库根目录解析,
# 端口可经环境变量或参数覆盖。任何装了 Java 21 的机器都能直接跑。
#
# 用法:
#   scripts/serve.sh                 # 前台启动,默认端口 8765
#   scripts/serve.sh --port 9000     # 指定端口
#   SZU_AGENT_PORT=9000 scripts/serve.sh
#   scripts/serve.sh --background    # 后台启动,PID 写入 logs/serve.pid
#   scripts/serve.sh --stop          # 停止后台服务
#
# Author: 王子豪 / 2023150090
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$REPO_ROOT/target/szu-agent-plugin.jar"
LOG_DIR="$REPO_ROOT/logs"
PID_FILE="$LOG_DIR/serve.pid"
PORT="${SZU_AGENT_PORT:-8765}"
MODE="foreground"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)        PORT="$2"; shift 2 ;;
        --background)  MODE="background"; shift ;;
        --stop)        MODE="stop"; shift ;;
        -h|--help)
            grep '^#' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "未知参数: $1" >&2; exit 2 ;;
    esac
done

mkdir -p "$LOG_DIR"

# Load project .env into this shell so the daemon inherits credentials.
# Values are never logged; only the fact that the file was loaded is printed.
ENV_FILE="$REPO_ROOT/.env"
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$ENV_FILE"
    set +a
    echo "已加载凭据文件: $ENV_FILE (密码内容不显示)"
fi

if [[ "$MODE" == "stop" ]]; then
    if [[ -f "$PID_FILE" ]] && kill "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "已停止后台服务 (PID $(cat "$PID_FILE"))"
        rm -f "$PID_FILE"
    else
        echo "没有正在运行的后台服务"
    fi
    exit 0
fi

if [[ ! -f "$JAR_PATH" ]]; then
    echo "找不到 jar: $JAR_PATH" >&2
    echo "请先构建:  mvn -q -DskipTests package" >&2
    exit 1
fi

CMD=(java -jar "$JAR_PATH" mcp serve --http --port "$PORT")

if [[ "$MODE" == "background" ]]; then
    "${CMD[@]}" > "$LOG_DIR/serve.log" 2>&1 &
    echo $! > "$PID_FILE"
    echo "后台服务已启动 (PID $!),端口 $PORT"
    echo "  健康检查: curl http://localhost:$PORT/health"
    echo "  日志:     $LOG_DIR/serve.log"
    echo "  停止:     scripts/serve.sh --stop"
else
    echo "前台启动 SZU Agent HTTP 服务,端口 $PORT (Ctrl-C 停止)"
    echo "  健康检查: curl http://localhost:$PORT/health"
    exec "${CMD[@]}"
fi
