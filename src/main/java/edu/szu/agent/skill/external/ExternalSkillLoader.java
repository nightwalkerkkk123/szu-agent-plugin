package edu.szu.agent.skill.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import edu.szu.agent.skill.Skill;
import edu.szu.agent.skill.Skills;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 外部 Skill 加载器，负责从文件系统扫描并注册独立 Skill。
 *
 * <p>加载路径来源：
 * <ol>
 *   <li>环境变量 {@code SZU_SKILL_PATH}（多个路径用当前系统 PATH 分隔符分隔）</li>
 *   <li>系统属性 {@code szu.skill.path}</li>
 * </ol>
 *
 * <p>每个路径下的<strong>直接子目录</strong>被视为一个 Skill 包；
 * 若该子目录中存在 {@code skill.yaml}，则解析并注册。
 *
 * <p>名称冲突处理：外部 Skill 与内部 Skill 同名时，外部 Skill
 * 优先（覆盖内部实现），并打印 warning 日志。
 *
 * // 编程技术: Stream API / Jackson YAML / 文件遍历
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class ExternalSkillLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalSkillLoader.class);

    private static final String MANIFEST_FILE = "skill.yaml";

    private ExternalSkillLoader() {
    }

    /**
     * 从默认来源加载外部 Skill 并注册到 {@link Skills} 单例。
     *
     * @return 成功注册的 Skill 数量
     * @since 0.6.0
     */
    public static int loadFromEnvironment() {
        return load(pathsFromEnvironment());
    }

    /**
     * 从给定路径列表加载外部 Skill。
     *
     * @param paths 要扫描的目录列表
     * @return 成功注册的 Skill 数量
     * @since 0.6.0
     */
    public static int load(List<Path> paths) {
        Skills registry = Skills.getInstance();
        Set<String> internalNames = registry.all().stream()
            .map(Skill::name)
            .collect(Collectors.toSet());

        int count = 0;
        for (Path root : paths) {
            if (!Files.isDirectory(root)) {
                LOG.debug("Skill path is not a directory: {}", root);
                continue;
            }
            count += loadFromRoot(root, registry, internalNames);
        }
        return count;
    }

    private static int loadFromRoot(Path root, Skills registry, Set<String> internalNames) {
        int count = 0;
        try (Stream<Path> entries = Files.list(root)) {
            List<Path> dirs = entries.filter(Files::isDirectory).toList();
            for (Path dir : dirs) {
                Path manifestPath = dir.resolve(MANIFEST_FILE);
                if (!Files.exists(manifestPath)) {
                    continue;
                }
                try {
                    ExternalSkillManifest manifest = parseManifest(manifestPath);
                    ExternalSkill task = new ExternalSkill(manifest);
                    Skill<Map<String, Object>> skill = new Skill<>(
                        manifest.name(), manifest.description(), task);

                    if (internalNames.contains(skill.name())) {
                        LOG.warn("External skill '{}' overrides internal skill with the same name", skill.name());
                        registry.register(skill);
                    } else {
                        registry.register(skill);
                    }
                    LOG.info("Loaded external skill '{}' from {}", skill.name(), dir);
                    count++;
                } catch (Exception e) {
                    LOG.warn("Failed to load external skill from {}: {}", dir, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to list skill path {}: {}", root, e.getMessage());
        }
        return count;
    }

    static ExternalSkillManifest parseManifest(Path manifestPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode root = mapper.readTree(manifestPath.toFile());

        String name = text(root, "name");
        String version = text(root, "version");
        String description = text(root, "description");
        String author = text(root, "author");
        String license = text(root, "license");
        String runtime = text(root, "runtime");
        Map<String, Object> inputSchema = schema(root.get("inputSchema"));
        Path directory = manifestPath.getParent();

        return new ExternalSkillManifest(
            name, version, description, author, license, runtime, inputSchema, directory);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of("type", "object", "additionalProperties", true);
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(node, LinkedHashMap.class);
    }

    private static List<Path> pathsFromEnvironment() {
        String env = System.getenv("SZU_SKILL_PATH");
        if (env == null || env.isBlank()) {
            env = System.getProperty("szu.skill.path", "");
        }
        if (env.isBlank()) {
            return List.of();
        }
        String separator = System.getProperty("path.separator");
        return Stream.of(env.split(separator))
            .filter(s -> !s.isBlank())
            .map(Path::of)
            .toList();
    }
}
