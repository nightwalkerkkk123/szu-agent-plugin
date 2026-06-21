package edu.szu.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HomeworkDownloadRequest")
class HomeworkDownloadRequestTest {

    @Test
    @DisplayName("builder() 必填字段缺失时抛 NPE")
    void builderRequiresHomeworkIdAndOutputDir() {
        assertThatThrownBy(() -> HomeworkDownloadRequest.builder().build())
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> HomeworkDownloadRequest.builder()
            .homeworkId("169193")
            .build())
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> HomeworkDownloadRequest.builder()
            .outputDir(Path.of("/tmp"))
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder() 默认 throttle=500ms / maxRetries=2")
    void builderAppliesDefaults() {
        var req = HomeworkDownloadRequest.builder()
            .homeworkId("169193")
            .outputDir(Path.of("/tmp/dl"))
            .build();

        assertThat(req.homeworkId()).isEqualTo("169193");
        assertThat(req.outputDir()).isEqualTo(Path.of("/tmp/dl"));
        assertThat(req.throttle()).isEqualTo(Duration.ofMillis(500));
        assertThat(req.maxRetries()).isEqualTo(2);
    }

    @Test
    @DisplayName("builder() 拒绝空白 homeworkId 与负数 maxRetries")
    void builderRejectsBlankAndNegative() {
        assertThatThrownBy(() -> HomeworkDownloadRequest.builder()
            .homeworkId("   ")
            .outputDir(Path.of("/tmp"))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("homeworkId");

        assertThatThrownBy(() -> HomeworkDownloadRequest.builder()
            .homeworkId("169193")
            .outputDir(Path.of("/tmp"))
            .maxRetries(-1)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxRetries");
    }
}
