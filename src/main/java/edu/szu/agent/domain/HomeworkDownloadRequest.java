package edu.szu.agent.domain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Parameters for a single attachment download operation.
 *
 * <p>Built via {@link Builder} so future params (e.g. proxy, concurrency,
 * filename template) can be added without breaking existing call sites.
 *
 * <p>// Design Pattern: Builder
 * // 编程技术: 不可变 + 链式构造
 *
 * @since 0.6.0
 * @author 王子豪
 */
public record HomeworkDownloadRequest(String homeworkId, Path outputDir,
                                      Duration throttle, int maxRetries) {

    /** Default inter-download throttle — 500ms is empirically safe for SZU LMS. */
    public static final Duration DEFAULT_THROTTLE = Duration.ofMillis(500);

    /** Default retry count for transient HTTP / IO failures. */
    public static final int DEFAULT_MAX_RETRIES = 2;

    public HomeworkDownloadRequest {
        Objects.requireNonNull(homeworkId, "homeworkId");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(throttle, "throttle");
        if (homeworkId.isBlank()) {
            throw new IllegalArgumentException("homeworkId must not be blank");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got " + maxRetries);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link HomeworkDownloadRequest}.
     *
     * <p>Defaults: {@code throttle = 500ms}, {@code maxRetries = 2}.
     */
    public static final class Builder {
        private String homeworkId;
        private Path outputDir;
        private Duration throttle = DEFAULT_THROTTLE;
        private int maxRetries = DEFAULT_MAX_RETRIES;

        public Builder homeworkId(String homeworkId) {
            this.homeworkId = homeworkId;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder throttle(Duration throttle) {
            this.throttle = throttle;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public HomeworkDownloadRequest build() {
            return new HomeworkDownloadRequest(homeworkId, outputDir, throttle, maxRetries);
        }
    }
}
