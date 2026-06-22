package edu.szu.agent.skill.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.error.Severity;
import edu.szu.agent.task.CampusTask;
import edu.szu.agent.task.TaskInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 外部 Skill 的执行器，通过调用目录中的入口脚本实现 {@link CampusTask}。
 *
 * <p>调用约定：
 * <pre>{@code
 *   <entryScript> <skillName> '<argumentsJson>'
 * }</pre>
 *
 * <p>脚本标准输出必须是统一 JSON 信封：
 * <pre>{@code
 *   {"success": true, "data": {...}}
 *   {"success": false, "errorCode": "...", "errorMessage": "..."}
 * }</pre>
 *
 * // 编程技术: 泛型 / ProcessBuilder / Jackson
 *
 * @since 0.2.0
 * @author 王子豪
 */
public final class ExternalSkill implements CampusTask<Map<String, Object>> {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalSkill.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 60L;

    private final ExternalSkillManifest manifest;
    private final ObjectMapper mapper;

    public ExternalSkill(ExternalSkillManifest manifest) {
        this(manifest, new ObjectMapper());
    }

    ExternalSkill(ExternalSkillManifest manifest, ObjectMapper mapper) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Returns the manifest of this external skill.
     *
     * @return the manifest
     * @since 0.2.0
     */
    public ExternalSkillManifest manifest() {
        return manifest;
    }

    @Override
    public String name() {
        return manifest.name();
    }

    @Override
    public String description() {
        return manifest.description();
    }

    @Override
    public Map<String, Object> execute(TaskInput input) {
        Path script = manifest.entryScript();
        if (!script.toFile().exists()) {
            throw new BookingException(
                ErrorCode.EXTERNAL_SKILL_NOT_FOUND,
                "Entry script not found: " + script);
        }

        String argsJson;
        try {
            argsJson = mapper.writeValueAsString(input.params());
        } catch (IOException e) {
            throw new BookingException(
                ErrorCode.EXTERNAL_SKILL_JSON_ERROR,
                "Failed to serialize arguments: " + e.getMessage());
        }

        ProcessBuilder pb = new ProcessBuilder(
            script.toAbsolutePath().toString(),
            manifest.name());
        pb.directory(manifest.directory().toFile());
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
            try (var writer = new java.io.OutputStreamWriter(
                process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(argsJson);
            }
        } catch (IOException e) {
            throw new BookingException(
                ErrorCode.EXTERNAL_SKILL_NOT_FOUND,
                "Failed to start external skill: " + e.getMessage());
        }

        String output;
        try {
            boolean finished = process.waitFor(timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BookingException(
                    ErrorCode.EXTERNAL_SKILL_TIMEOUT,
                    "External skill timed out after " + timeoutSeconds() + " seconds");
            }
            output = readAll(process);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BookingException(
                ErrorCode.EXTERNAL_SKILL_TIMEOUT,
                "External skill interrupted");
        }

        if (process.exitValue() != 0) {
            throw new BookingException(
                ErrorCode.EXTERNAL_SKILL_JSON_ERROR,
                "External skill exited with " + process.exitValue() + ": " + output);
        }

        return parseOutput(output);
    }

    private Map<String, Object> parseOutput(String output) {
        String trimmed = output.trim();
        if (trimmed.isEmpty()) {
            throw new BookingException(
                ErrorCode.EXTERNAL_SKILL_JSON_ERROR,
                "External skill produced empty output");
        }
        try {
            Map<String, Object> envelope = mapper.readValue(trimmed, new TypeReference<>() {});
            Boolean success = (Boolean) envelope.get("success");
            if (Boolean.TRUE.equals(success)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                return data == null ? Map.of() : Map.copyOf(data);
            }
            String code = String.valueOf(envelope.getOrDefault("errorCode", "UNKNOWN"));
            String message = String.valueOf(envelope.getOrDefault("errorMessage", "External skill failed"));
            ErrorCode errorCode = parseErrorCode(code);
            throw new BookingException(errorCode, message);
        } catch (IOException e) {
            throw new BookingException(
                ErrorCode.EXTERNAL_SKILL_JSON_ERROR,
                "Invalid JSON from external skill: " + e.getMessage() + " | output: " + trimmed);
        }
    }

    private static ErrorCode parseErrorCode(String code) {
        try {
            return ErrorCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown error code from external skill: {}", code);
            return ErrorCode.UNKNOWN;
        }
    }

    private static String readAll(Process process) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read external skill output", e);
        }
        return sb.toString();
    }

    private static long timeoutSeconds() {
        String env = System.getenv("SZU_SKILL_TIMEOUT");
        if (env == null || env.isBlank()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        try {
            long v = Long.parseLong(env.trim());
            return v > 0 ? v : DEFAULT_TIMEOUT_SECONDS;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }
}
