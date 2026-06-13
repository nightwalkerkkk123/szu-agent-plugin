package edu.szu.agent.mcp;

import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;

import java.util.List;
import java.util.Map;

/**
 * MCP {@code tools/list} implementation.
 *
 * <p>Per ADR-0001 D5: this is a thin wrapper over the Skills registry.
 * An MCP host (e.g. Claude Code, OpenClaw) imports the JSON returned
 * by {@link #listTools()} and uses it to discover available tools.
 *
 * <p>This class is stateless and thread-safe — all state lives in
 * the {@link Skills} singleton.
 *
 * // 编程技术: 泛型(纯委派给 Skills 单例,本类不实现 Singleton)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class MCPToolProvider {

    private MCPToolProvider() {
    }

    /**
     * Returns the MCP {@code tools/list} response, sourced from the
     * Skills registry.
     *
     * @return a map with {@code schemaVersion} and {@code tools} keys,
     *         ready for Jackson serialization
     * @since 0.1.0
     */
    public static Map<String, Object> listTools() {
        List<Skill<?>> skills = Skills.getInstance().all();
        return ToolSchema.toolsList(skills);
    }
}
