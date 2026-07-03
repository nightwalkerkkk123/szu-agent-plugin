package edu.szu.agent.error;

/**
 * Severity tier — 4 levels, used by observability to pick trace color
 * and by logback to filter messages.
 *
 * <p>Per ADR-0006 §二.3: separate enum, JSON-serialized by name
 * ({@code "HIGH"}). Ordering is meaningful: a higher ordinal = more severe.
 *
 * // 编程技术: 枚举(序数 = 严重度排序)
 *
 * @since 0.6.0
 * @author 王子豪
 */
public enum Severity {
    /** Informational, no action needed. */
    LOW,
    /** Operational issue, retry may resolve. */
    MEDIUM,
    /** Significant issue, manual intervention may be required. */
    HIGH,
    /** Hard failure, account / system state compromised. */
    CRITICAL
}
