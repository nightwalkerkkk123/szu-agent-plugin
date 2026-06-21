package edu.szu.agent.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCategoryTest {

    @Test
    void allCategoriesHaveDisplayNames() {
        assertThat(KnowledgeCategory.CAMPUS_BASICS.displayName()).isEqualTo("校园基础");
        assertThat(KnowledgeCategory.DINING.displayName()).isEqualTo("餐饮服务");
        assertThat(KnowledgeCategory.LIBRARY.displayName()).isEqualTo("图书馆");
        assertThat(KnowledgeCategory.ACADEMICS.displayName()).isEqualTo("学业选课");
        assertThat(KnowledgeCategory.FAQ.displayName()).isEqualTo("常见问题");
    }

    @Test
    void valuesMatchExpectedCount() {
        assertThat(KnowledgeCategory.values()).hasSize(5);
    }
}
