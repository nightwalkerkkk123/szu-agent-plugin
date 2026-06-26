package edu.szu.agent.task;

import edu.szu.agent.cli.Main;
import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToolDocsGenerator}.
 *
 * <p>// 编程技术: JUnit 5 / AssertJ / 泛型
 *
 * @since 0.5.0
 * @author 王子豪
 */
class ToolDocsGeneratorTest {

    @BeforeEach
    void registerSkills() {
        Skills.reset();
        Main.registerDefaultSkills();
    }

    @AfterEach
    void cleanup() {
        Skills.reset();
    }

    @Test
    @DisplayName("renderMarkdown includes sections, parameters, enums and examples")
    void renderMarkdownIncludesCoreSections() {
        Skill<?> booking = skill("booking_venue");

        String markdown = ToolDocsGenerator.renderMarkdown(booking);

        assertThat(markdown)
            .startsWith("# booking_venue")
            .contains("## 参数", "## 枚举", "## 示例", "## 返回值", "## 常见错误", "## 相关文档")
            .contains("`campus`", "`sport`", "`YUEHAI`", "\"name\": \"booking_venue\"")
            .contains("BookingResult");
    }

    @Test
    @DisplayName("renderMarkdown handles empty annotations")
    void renderMarkdownHandlesEmptyAnnotations() {
        Skill<String> dummy = new Skill<>("dummy_tool", "dummy description", new CampusTask<>() {
            @Override
            public String name() {
                return "dummy_tool";
            }

            @Override
            public String description() {
                return "dummy description";
            }

            @Override
            public String execute(TaskInput input) {
                return "ok";
            }
        });

        String markdown = ToolDocsGenerator.renderMarkdown(dummy);

        assertThat(markdown)
            .contains("# dummy_tool", "暂无示例。", "暂无补充说明。", "暂无。")
            .contains("## 参数", "## 相关文档");
    }

    @Test
    @DisplayName("all built-in skills render complete reference pages")
    void allBuiltInSkillsRenderCompleteDocs() {
        List<Skill<?>> skills = Skills.getInstance().all();

        assertThat(skills).hasSize(8);
        assertThat(skills)
            .allSatisfy(skill -> {
                String markdown = ToolDocsGenerator.renderMarkdown(skill);
                assertThat(markdown)
                    .contains("# " + skill.name())
                    .contains("## 参数", "## 示例", "## 返回值", "## 常见错误", "## 相关文档");
                assertThat(markdown.length()).isGreaterThan(300);
            });
    }

    private static Skill<?> skill(String name) {
        return Skills.getInstance().all().stream()
            .filter(skill -> name.equals(skill.name()))
            .findFirst()
            .orElseThrow();
    }
}
