package edu.szu.agent.skill.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * 外部 Skill 的元数据，对应 skill.yaml 内容。
 *
 * <p>一个外部 Skill 就是一个目录，里面包含 {@code skill.yaml}
 * 和平台相关的入口脚本（{@code run} 或 {@code run.bat}）。
 * 本类只保存从 YAML 解析出来的元数据；实际执行由
 * {@link ExternalSkill} 负责。
 *
 * // 编程技术: record(Java 16+) / Jackson 反序列化
 *
 * @param name Skill 唯一名称（snake_case）
 * @param version 版本号
 * @param description 简短描述
 * @param author 作者
 * @param license 许可证
 * @param runtime 执行环境提示（如 python3、bash、node）
 * @param inputSchema MCP JSON Schema 形状
 * @param directory Skill 所在目录
 * @since 0.2.0
 * @author 王子豪
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalSkillManifest(
    String name,
    String version,
    String description,
    String author,
    String license,
    String runtime,
    Map<String, Object> inputSchema,
    Path directory
) {

    public ExternalSkillManifest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be blank");
        }
        if (inputSchema == null) {
            inputSchema = Map.of("type", "object", "additionalProperties", true);
        }
    }

    /**
     * 返回当前平台优先使用的入口脚本路径。
     *
     * <p>策略：
     * <ul>
     *   <li>Windows 上优先 {@code run.bat}，不存在则回退 {@code run}
     *       （方便 Git Bash / WSL 环境执行 bash 脚本）。</li>
     *   <li>非 Windows 上优先 {@code run}，不存在则回退 {@code run.bat}。</li>
     * </ul>
     *
     * @return 入口脚本 {@link Path}
     * @since 0.2.0
     */
    public Path entryScript() {
        Path unix = directory.resolve("run");
        Path windows = directory.resolve("run.bat");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        if (isWindows) {
            if (windows.toFile().exists()) {
                return windows;
            }
            return unix;
        }
        if (unix.toFile().exists()) {
            return unix;
        }
        return windows;
    }

    /**
     * 判断入口脚本是否存在（任一平台）。
     *
     * @return true if either {@code run} or {@code run.bat} exists
     * @since 0.2.0
     */
    public boolean hasEntryScript() {
        return directory.resolve("run").toFile().exists()
            || directory.resolve("run.bat").toFile().exists();
    }
}
