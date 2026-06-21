package edu.szu.agent.client.session;

/**
 * Outcome of a {@link SessionProbe} check.
 *
 * <p>// 编程技术: sealed interface + record
 *
 * @since 0.1.0
 * @author 王子豪
 */
public sealed interface SessionResult {

    record Fresh() implements SessionResult {}

    record Stale(String reason) implements SessionResult {}
}
