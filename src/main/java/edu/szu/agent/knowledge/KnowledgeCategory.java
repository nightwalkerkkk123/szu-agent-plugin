package edu.szu.agent.knowledge;

/**
 * Knowledge-base category — mirrors the five document classes described
 * in {@code docs/final-report.md} §3.6.
 *
 * @since 0.6.0
 * @author 王子豪
 */
public enum KnowledgeCategory {
    CAMPUS_BASICS("校园基础"),
    DINING("餐饮服务"),
    LIBRARY("图书馆"),
    ACADEMICS("学业选课"),
    FAQ("常见问题");

    private final String displayName;

    KnowledgeCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
