/**
 * Direct HTTP clients and traffic recording utilities for SZU intranet APIs.
 *
 * <p>Provides a migration path away from {@link edu.szu.agent.browser.BrowserLifecycle}
 * for endpoints whose request/response contracts are known or can be reverse-engineered.
 *
 * // Design Pattern: Adapter (HTTP transport as an alternative to browser automation)
 * // 编程技术: 泛型 / 不可变 record / Lambda / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
package edu.szu.agent.client.http;
