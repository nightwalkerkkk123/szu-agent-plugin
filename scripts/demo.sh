#!/usr/bin/env bash
# scripts/demo.sh — 课堂演示固定流程
#
# 用途:面向"面向对象高级编程"课堂演示设计的可重复脚本。
#
# 演示路径:
#   1. 环境健康检查(Java / jar / .env / 网络可达)
#   2. dry-run 烟雾测试(< 5s,验证 CLI 链路没问题)
#   3. 课堂演示真跑(Playwright 控制本地浏览器)— 用户手动确认后执行
#   4. 输出 trace_id + 截图路径 + 退出码,便于教师追溯
#
# 用法:
#   scripts/demo.sh [--smoke-only]    # 只跑环境检查 + dry-run,不真跑
#   scripts/demo.sh [--full]          # 完整流程(默认)
#   scripts/demo.sh --help
#
# 演示兜底(对照 ADR-0001 D8):
#   - 提前 1-2 天在演示账号上"故意不预约任何项目"留弹药
#   - 准备 2-3 个备用项目 + 备用时段
#   - 真演示失败时切录屏 backup
#   - 演示后 5 分钟内手工 ehall 取消占位场地(对照 HARNESS_BACKLOG ID-002)
#
# Author: 王子豪 / 2023150090
# Since:  0.1.0

set -euo pipefail

# ----- 路径与配置 ------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$REPO_ROOT/target/szu-agent-plugin.jar"
ENV_FILE="${SZU_ENV_FILE:-$REPO_ROOT/.env}"
LOG_DIR="$REPO_ROOT/logs"
LOG_FILE="$LOG_DIR/demo-$(date +%Y%m%d-%H%M%S).log"

# 演示参数(可通过环境变量覆盖,演示日临时调整不用改脚本)
DEMO_USERNAME="${DEMO_USERNAME:-2023150090}"
DEMO_CAMPUS="${DEMO_CAMPUS:-YUEHAI}"
DEMO_SPORT="${DEMO_SPORT:-TENNIS}"
DEMO_DATE_OFFSET="${DEMO_DATE_OFFSET:-1}"        # 1 = 明天,避开当天满场
DEMO_TIME_SLOT="${DEMO_TIME_SLOT:-19:00-20:00}"
DEMO_PREFERRED_VENUE="${DEMO_PREFERRED_VENUE:-1}"

# 颜色(终端可读性 — 课堂演示让评分者看清楚每一步状态)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ----- 日志与输出助手 --------------------------------------------------------

mkdir -p "$LOG_DIR"

log() {
    local level="$1"
    shift
    local msg="$*"
    local ts
    ts="$(date +'%Y-%m-%dT%H:%M:%S')"
    case "$level" in
        INFO)  echo -e "${BLUE}[${ts}] [INFO]${NC}  $msg" | tee -a "$LOG_FILE" ;;
        OK)    echo -e "${GREEN}[${ts}] [ OK ]${NC}  $msg" | tee -a "$LOG_FILE" ;;
        WARN)  echo -e "${YELLOW}[${ts}] [WARN]${NC}  $msg" | tee -a "$LOG_FILE" ;;
        FAIL)  echo -e "${RED}[${ts}] [FAIL]${NC}  $msg" | tee -a "$LOG_FILE" ;;
        STEP)  echo -e "\n${BLUE}=== $msg ===${NC}" | tee -a "$LOG_FILE" ;;
    esac
}

die() {
    log FAIL "$*"
    log INFO "完整日志: $LOG_FILE"
    exit 1
}

confirm() {
    local prompt="$1"
    local response
    echo -en "${YELLOW}${prompt} [y/N]:${NC} "
    read -r response
    [[ "$response" =~ ^[Yy] ]]
}

# ----- 步骤 1:环境健康检查 ---------------------------------------------------

step_env_check() {
    log STEP "Step 1/4: 环境健康检查"

    # Java 版本
    if ! command -v java &> /dev/null; then
        die "未找到 java 命令,请安装 JDK 21+"
    fi
    local java_version
    java_version="$(java -version 2>&1 | head -1)"
    log INFO "Java: $java_version"
    # 简单版本检查:只接受 21+
    if java -version 2>&1 | grep -qE 'version "(1\.[0-9]\.|9\.|10\.|11\.|12\.|13\.|14\.|15\.|16\.|17\.|18\.|19\.|20\.)'; then
        die "Java 版本过低,需要 21+"
    fi
    log OK "Java 21+ ✓"

    # jar 存在
    if [[ ! -f "$JAR_PATH" ]]; then
        log WARN "jar 不存在: $JAR_PATH"
        log INFO "尝试 mvn package 构建..."
        (cd "$REPO_ROOT" && mvn -q -DskipTests package) || die "mvn package 失败"
    fi
    log OK "jar: $JAR_PATH ($(du -h "$JAR_PATH" | cut -f1))"

    # .env 文件
    if [[ ! -f "$ENV_FILE" ]]; then
        log WARN ".env 不存在: $ENV_FILE"
        log INFO "课堂演示前请 cp .env.example .env 并填入 SZU_PASSWORD_${DEMO_USERNAME}"
        log INFO "(dry-run 不需要 .env;真跑必须有)"
    else
        # 不打印密码内容,只确认对应 key 存在
        if grep -q "^SZU_PASSWORD_${DEMO_USERNAME}=" "$ENV_FILE"; then
            log OK ".env 包含 SZU_PASSWORD_${DEMO_USERNAME} ✓"
        else
            log WARN ".env 不含 SZU_PASSWORD_${DEMO_USERNAME},真跑会失败"
        fi
    fi
}

# ----- 步骤 2:dry-run 烟雾测试 -----------------------------------------------

step_smoke() {
    log STEP "Step 2/4: dry-run 烟雾测试(FakeBrowser,< 5s)"
    log INFO "命令: java -jar ... booking venue --dry-run --format json"

    local stdout
    local exit_code
    set +e
    stdout="$(java -jar "$JAR_PATH" booking venue \
        --username "$DEMO_USERNAME" \
        --campus "$DEMO_CAMPUS" \
        --sport "$DEMO_SPORT" \
        --date "$DEMO_DATE_OFFSET" \
        --time-slot "$DEMO_TIME_SLOT" \
        --preferred-venue "$DEMO_PREFERRED_VENUE" \
        --dry-run --format json 2>&1)"
    exit_code=$?
    set -e

    echo "$stdout" >> "$LOG_FILE"

    if [[ $exit_code -ne 0 ]]; then
        log FAIL "dry-run 退出码 $exit_code(预期 0)"
        echo "$stdout"
        die "dry-run 失败 — 不要继续真跑"
    fi

    # 解析 JSON 最后一行(忽略 logback INFO/WARN 输出)
    local json_line
    json_line="$(echo "$stdout" | tail -1)"
    if echo "$json_line" | grep -q '"success":true'; then
        log OK "dry-run JSON: $json_line"
    else
        log FAIL "dry-run JSON 格式异常"
        echo "$json_line"
        exit 1
    fi
}

# ----- 步骤 3:课堂演示真跑 ---------------------------------------------------

step_real_run() {
    log STEP "Step 3/4: 课堂演示真跑(Playwright)"

    # 演示日参数确认 — 让评分者看清楚要预约什么
    cat <<EOF | tee -a "$LOG_FILE"

  演示参数:
    学号:     $DEMO_USERNAME
    校区:     $DEMO_CAMPUS
    项目:     $DEMO_SPORT
    日期偏移: $DEMO_DATE_OFFSET ($(date -d "+${DEMO_DATE_OFFSET} days" +'%Y-%m-%d' 2>/dev/null || date -v +"${DEMO_DATE_OFFSET}d" +'%Y-%m-%d' 2>/dev/null || echo 'TBD'))
    时段:     $DEMO_TIME_SLOT
    场地序号: $DEMO_PREFERRED_VENUE

  环境变量覆盖示例:
    DEMO_SPORT=BADMINTON DEMO_TIME_SLOT=20:00-21:00 ./scripts/demo.sh --full

  ADR-0001 D8 演示兜底已确认:
    [ ] 演示账号当天未预约任何项目(留弹药)
    [ ] 备用项目/时段已备好
    [ ] 录屏 backup 已就位
    [ ] 演示后 5 分钟内会手工取消(避免占位)

EOF

    if ! confirm "上述参数确认无误,开始真跑?"; then
        log INFO "用户取消真跑,演示流程终止"
        return 10
    fi

    if [[ ! -f "$ENV_FILE" ]]; then
        die "真跑必须有 .env 文件: $ENV_FILE"
    fi

    log INFO "执行真跑命令(本地浏览器会启动)..."
    local stdout
    local exit_code
    set +e
    # 注意:不传 --dry-run,走 Playwright 真路径
    stdout="$(java -jar "$JAR_PATH" booking venue \
        --username "$DEMO_USERNAME" \
        --campus "$DEMO_CAMPUS" \
        --sport "$DEMO_SPORT" \
        --date "$DEMO_DATE_OFFSET" \
        --time-slot "$DEMO_TIME_SLOT" \
        --preferred-venue "$DEMO_PREFERRED_VENUE" \
        --env-file "$ENV_FILE" \
        --format json 2>&1)"
    exit_code=$?
    set -e

    echo "$stdout" >> "$LOG_FILE"

    # 提取 JSON 输出(最后一行,前面是 logback 日志)
    local json_line
    json_line="$(echo "$stdout" | tail -1)"

    case $exit_code in
        0)
            log OK "真跑成功 ✓"
            log INFO "JSON: $json_line"
            ;;
        1)
            log FAIL "业务失败(退出码 1)— 可能是场地占用/无可用时段"
            log INFO "JSON: $json_line"
            ;;
        2)
            log FAIL "参数错误(退出码 2)— 检查 --campus/--sport 等枚举"
            ;;
        3)
            log FAIL "环境错误(退出码 3)— 凭证或 .env 问题"
            ;;
        4)
            log FAIL "浏览器错误(退出码 4)— Playwright 崩溃"
            log INFO "查看截图目录: $REPO_ROOT/logs/screenshots/"
            ;;
        10)
            log INFO "用户取消"
            ;;
        *)
            log FAIL "未知退出码 $exit_code"
            ;;
    esac

    return $exit_code
}

# ----- 步骤 4:总结 -----------------------------------------------------------

step_summary() {
    log STEP "Step 4/4: 演示总结"
    log INFO "完整日志: $LOG_FILE"
    log INFO "JaCoCo 覆盖率报告: $REPO_ROOT/target/site/jacoco/index.html"
    log INFO "类图: $REPO_ROOT/docs/class-diagram.puml"
    log INFO "提案文档: $REPO_ROOT/design/2023150090_王子豪_大作业自拟题目.md"
    log INFO "grep 证据表: $REPO_ROOT/docs/grep-evidence.md"
    log INFO ""
    log INFO "演示后清理(对照 HARNESS_BACKLOG ID-002):"
    log INFO "  → 5 分钟内访问 ehall 手工取消占位场地"
    log INFO ""
}

# ----- 入口 -----------------------------------------------------------------

usage() {
    cat <<EOF
SZU Agent Plugin — 课堂演示脚本

用法:
  $0                  完整流程(env-check + dry-run + 真跑 + 总结)
  $0 --smoke-only     只跑前两步(env-check + dry-run),不启动浏览器
  $0 --full           完整流程(等同无参数)
  $0 --help           显示此帮助

环境变量:
  DEMO_USERNAME       学号           (默认 2023150090)
  DEMO_CAMPUS         校区           (默认 YUEHAI)
  DEMO_SPORT          运动项目       (默认 TENNIS)
  DEMO_DATE_OFFSET    日期偏移       (默认 1,即明天)
  DEMO_TIME_SLOT      时间段         (默认 19:00-20:00)
  DEMO_PREFERRED_VENUE 场地序号      (默认 1)
  SZU_ENV_FILE        .env 路径      (默认 ./.env)

退出码:
  0  全部成功
  1  业务失败
  2  参数错误
  3  环境错误
  4  浏览器错误
  10 用户取消(在确认提示处选 N)
EOF
}

main() {
    local mode="${1:-}"
    case "$mode" in
        -h|--help)
            usage
            exit 0
            ;;
        --smoke-only)
            step_env_check
            step_smoke
            log STEP "Smoke-only 模式完成 ✓"
            exit 0
            ;;
        ""|--full)
            step_env_check
            step_smoke
            local rc=0
            step_real_run || rc=$?
            step_summary
            exit "$rc"
            ;;
        *)
            log FAIL "未知参数: $mode"
            usage
            exit 2
            ;;
    esac
}

main "$@"
