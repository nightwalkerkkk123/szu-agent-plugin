#!/usr/bin/env bash
# scripts/grep-runs.sh — 复现 docs/grep-evidence.md 的所有数字
#
# 用法:从仓库根目录运行
#   ./scripts/grep-runs.sh
#
# 退出码:0 = 所有 grep 命中数与 docs/grep-evidence.md 预期一致
#        1 = 不一致(打印差异并列出实际命中文件)
#
# 关联文档:docs/grep-evidence.md §三
#          设计模式 24 文件 / 编程技术 46 文件(2026-06-14 P1 wrapper 后)

set -euo pipefail

SRC="${SRC:-src/main/java}"
if [[ ! -d "$SRC" ]]; then
    echo "ERROR: $SRC 不存在,请从仓库根目录运行"
    exit 1
fi

# 文档里登记的预期值
declare -A EXPECTED=(
    [Builder]=1 [Singleton]=3 [Strategy]=18 [Adapter]=2
    [泛型]=9 [枚举]=16 [注解]=4 [重载]=4 [抽象类]=1 [Lambda]=17
)

PATTERNS_TOTAL=24
TECHNIQUES_TOTAL=46

failures=0

echo "== 4 设计模式 =="
for k in Builder Singleton Strategy Adapter; do
    n=$(grep -rln "// Design Pattern: $k" "$SRC" 2>/dev/null | wc -l | tr -d ' ')
    exp=${EXPECTED[$k]}
    if [[ "$n" == "$exp" ]]; then
        echo "  ✓ $k: $n (expected $exp)"
    else
        echo "  ✗ $k: $n (expected $exp)"
        grep -rln "// Design Pattern: $k" "$SRC" 2>/dev/null
        failures=$((failures + 1))
    fi
done

total_patterns=$(grep -rln '// Design Pattern:' "$SRC" 2>/dev/null | wc -l | tr -d ' ')
if [[ "$total_patterns" == "$PATTERNS_TOTAL" ]]; then
    echo "  ✓ TOTAL: $total_patterns (expected $PATTERNS_TOTAL)"
else
    echo "  ✗ TOTAL: $total_patterns (expected $PATTERNS_TOTAL)"
    failures=$((failures + 1))
fi

echo ""
echo "== 6 编程技术 =="
for k in 泛型 枚举 注解 重载 抽象类 Lambda; do
    n=$(grep -rln "// 编程技术:.*$k" "$SRC" 2>/dev/null | wc -l | tr -d ' ')
    exp=${EXPECTED[$k]}
    if [[ "$n" == "$exp" ]]; then
        echo "  ✓ $k: $n (expected $exp)"
    else
        echo "  ✗ $k: $n (expected $exp)"
        grep -rln "// 编程技术:.*$k" "$SRC" 2>/dev/null
        failures=$((failures + 1))
    fi
done

total_techniques=$(grep -rln '// 编程技术:' "$SRC" 2>/dev/null | wc -l | tr -d ' ')
if [[ "$total_techniques" == "$TECHNIQUES_TOTAL" ]]; then
    echo "  ✓ TOTAL: $total_techniques (expected $TECHNIQUES_TOTAL)"
else
    echo "  ✗ TOTAL: $total_techniques (expected $TECHNIQUES_TOTAL)"
    failures=$((failures + 1))
fi

echo ""
if [[ $failures -eq 0 ]]; then
    echo "ALL OK — 数字与 docs/grep-evidence.md 一致"
    exit 0
else
    echo "FAIL — $failures 处不一致,请更新 docs/grep-evidence.md 或源码注释"
    exit 1
fi
