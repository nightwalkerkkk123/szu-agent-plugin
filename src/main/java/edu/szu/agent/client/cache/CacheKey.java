package edu.szu.agent.client.cache;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Composite cache key: scope + key + schemaVersion.
 *
 * <p>The scope is validated against a whitelist regex to prevent path traversal.
 * The key is a free-form string safe for use as a filename.
 *
 * <p>Filename: {@code <scope>-<key>.json} where key is sanitized by replacing
 * problematic characters with {@code _}.
 *
 * // 编程技术: record / 输入校验
 *
 * @since 0.3.0
 * @author 王子豪
 */
public record CacheKey(
    String scope,
    String key,
    int schemaVersion
) {
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]+");

    /**
     * Creates a cache key, validating scope against the whitelist.
     *
     * @param scope        cache scope (must match {@code [a-z][a-z0-9_]+})
     * @param key          cache key (free-form, sanitized for filesystem)
     * @param schemaVersion payload schema version
     * @since 0.3.0
     * @author 王子豪
     */
    public CacheKey {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(key, "key");
        if (!CacheStore.SCOPE_PATTERN.matcher(scope).matches()) {
            throw new IllegalArgumentException(
                "scope must match [a-z][a-z0-9_]+, got: " + scope);
        }
        // Sanitize key for filesystem use
        key = SAFE_KEY_PATTERN.matcher(key).replaceAll("_");
    }

    /**
     * Returns the sanitized filename for this cache key.
     *
     * @return {@code "<scope>-<key>.json"}
     * @since 0.3.0
     * @author 王子豪
     */
    public String filename() {
        return scope + "-" + key + ".json";
    }
}
