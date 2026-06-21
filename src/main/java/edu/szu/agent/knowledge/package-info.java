/**
 * Knowledge-base domain and query infrastructure for the {@code kb_query} Skill.
 *
 * <p>The knowledge base is intentionally decoupled from browser automation:
 * it loads local Markdown documents, parses YAML frontmatter, and answers
 * queries via pluggable matching strategies.
 *
 * // 设计模式: Strategy (MatchingStrategy) / Builder (KnowledgeDocBuilder)
 * // 编程技术: 枚举 / 泛型 / Lambda + Stream
 *
 * @since 0.2.0
 * @author 王子豪
 */
package edu.szu.agent.knowledge;
