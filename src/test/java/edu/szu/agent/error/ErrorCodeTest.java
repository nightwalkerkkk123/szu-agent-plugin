package edu.szu.agent.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ErrorCode} enum metadata.
 *
 * <p>Per ADR-0006 §二.1: 34 values. Per §二.2: 5 metadata fields per constant.
 *
 * @since 0.1.0
 * @author 王子豪
 */
@DisplayName("ErrorCode enum")
class ErrorCodeTest {

    @Test
    @DisplayName("has exactly 38 constants")
    void hasThirtyEightConstants() {
        // 14 original + 3 SESSION_* + 3 ATTACHMENT_* + 5 SCHEDULE_*
        // + 3 CHAOXING_* + 2 EXAM_* + 2 KNOWLEDGE_* + 1 CALENDAR_*
        // + 2 NOTICE_* + 3 EXTERNAL_SKILL_* = 38
        assertThat(ErrorCode.values()).hasSize(38);
    }

    @Test
    @DisplayName("SESSION_NOT_FOUND 元数据正确")
    void sessionNotFoundMetadata() {
        ErrorCode c = ErrorCode.SESSION_NOT_FOUND;
        assertThat(c.severity()).isEqualTo(Severity.LOW);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("无持久化登录态");
    }

    @Test
    @DisplayName("SESSION_READ_FAILED 元数据正确")
    void sessionReadFailedMetadata() {
        ErrorCode c = ErrorCode.SESSION_READ_FAILED;
        assertThat(c.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("持久化登录态损坏");
    }

    @Test
    @DisplayName("SESSION_WRITE_FAILED 元数据正确")
    void sessionWriteFailedMetadata() {
        ErrorCode c = ErrorCode.SESSION_WRITE_FAILED;
        assertThat(c.severity()).isEqualTo(Severity.LOW);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("持久化登录态写入失败");
    }

    @Test
    @DisplayName("ATTACHMENT_NOT_FOUND 元数据正确 (US-008)")
    void attachmentNotFoundMetadata() {
        ErrorCode c = ErrorCode.ATTACHMENT_NOT_FOUND;
        assertThat(c.severity()).isEqualTo(Severity.LOW);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("作业无附件");
    }

    @Test
    @DisplayName("ATTACHMENT_DOWNLOAD_FAILED 元数据正确 (US-008)")
    void attachmentDownloadFailedMetadata() {
        ErrorCode c = ErrorCode.ATTACHMENT_DOWNLOAD_FAILED;
        assertThat(c.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(c.isRetryable()).isTrue();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isTrue();
        assertThat(c.hint()).isEqualTo("附件下载失败");
    }

    @Test
    @DisplayName("OUTPUT_DIR_INVALID 元数据正确 (US-008)")
    void outputDirInvalidMetadata() {
        ErrorCode c = ErrorCode.OUTPUT_DIR_INVALID;
        assertThat(c.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("输出目录非法");
    }

    @Test
    @DisplayName("NETWORK_TIMEOUT is retryable but not screenshot-worthy")
    void networkTimeoutMetadata() {
        ErrorCode code = ErrorCode.NETWORK_TIMEOUT;

        assertThat(code.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(code.isRetryable()).isTrue();
        assertThat(code.shouldSwitchAccount()).isFalse();
        assertThat(code.shouldScreenshot()).isFalse();
        assertThat(code.hint()).isEqualTo("网络超时");
    }

    @Test
    @DisplayName("PASSWORD_INCORRECT is non-retryable and triggers account switch")
    void passwordIncorrectMetadata() {
        ErrorCode code = ErrorCode.PASSWORD_INCORRECT;

        assertThat(code.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(code.isRetryable()).isFalse();
        assertThat(code.shouldSwitchAccount()).isTrue();
        assertThat(code.shouldScreenshot()).isTrue();
    }

    @Test
    @DisplayName("INVALID_REQUEST is not retryable (user error, must fix input)")
    void invalidRequestMetadata() {
        ErrorCode code = ErrorCode.INVALID_REQUEST;

        assertThat(code.severity()).isEqualTo(Severity.LOW);
        assertThat(code.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("EXTERNAL_SKILL_NOT_FOUND 元数据正确")
    void externalSkillNotFoundMetadata() {
        ErrorCode c = ErrorCode.EXTERNAL_SKILL_NOT_FOUND;
        assertThat(c.severity()).isEqualTo(Severity.LOW);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("外部 Skill 入口脚本缺失");
    }

    @Test
    @DisplayName("EXTERNAL_SKILL_TIMEOUT 元数据正确")
    void externalSkillTimeoutMetadata() {
        ErrorCode c = ErrorCode.EXTERNAL_SKILL_TIMEOUT;
        assertThat(c.severity()).isEqualTo(Severity.LOW);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("外部 Skill 执行超时");
    }

    @Test
    @DisplayName("EXTERNAL_SKILL_JSON_ERROR 元数据正确")
    void externalSkillJsonErrorMetadata() {
        ErrorCode c = ErrorCode.EXTERNAL_SKILL_JSON_ERROR;
        assertThat(c.severity()).isEqualTo(Severity.LOW);
        assertThat(c.isRetryable()).isFalse();
        assertThat(c.shouldSwitchAccount()).isFalse();
        assertThat(c.shouldScreenshot()).isFalse();
        assertThat(c.hint()).isEqualTo("外部 Skill 输出非法 JSON");
    }

    @Test
    @DisplayName("all constants expose non-blank hint")
    void allConstantsHaveHint() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.hint())
                .as("hint for %s", code.name())
                .isNotBlank();
            assertThat(code.severity())
                .as("severity for %s", code.name())
                .isNotNull();
        }
    }
}
