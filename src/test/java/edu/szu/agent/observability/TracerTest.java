package edu.szu.agent.observability;

import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link Tracer}.
 *
 * <p>Per ADR-0007 D4: {@link Tracer#recordFailure(ErrorCode, String, Optional)}
 * is the only public failure-recording method — it does NOT accept
 * {@code Throwable} or {@code BookingException}, keeping the
 * observability &harr; error seam clean.
 *
 * @since 0.6.0
 * @author 王子豪
 */
class TracerTest {

    @BeforeEach
    void resetSingleton() {
        Tracer.getInstance().reset();
        MDC.clear();
    }

    @AfterEach
    void cleanupSingleton() {
        Tracer.getInstance().reset();
        MDC.clear();
    }

    // ---------- singleton ----------

    @Test
    @DisplayName("getInstance() returns the same instance on repeated calls")
    void getInstance_returnsSameInstance() {
        Tracer a = Tracer.getInstance();
        Tracer b = Tracer.getInstance();
        assertThat(a).isSameAs(b);
    }

    // ---------- trace_id lifecycle ----------

    @Test
    @DisplayName("generateTraceId() returns a non-blank id")
    void generateTraceId_returnsNonBlank() {
        String id = Tracer.getInstance().generateTraceId();
        assertThat(id).isNotBlank();
    }

    @Test
    @DisplayName("generateTraceId() returns distinct ids on repeated calls")
    void generateTraceId_returnsDistinctIds() {
        Tracer tracer = Tracer.getInstance();
        String a = tracer.generateTraceId();
        String b = tracer.generateTraceId();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("generateTraceId() matches the documented format YYYYMMDD-XXXXXX")
    void generateTraceId_matchesDocumentedFormat() {
        String id = Tracer.getInstance().generateTraceId();
        // YYYYMMDD (8 digits) - XXXXXX (6 alphanumeric chars)
        assertThat(id).matches("^\\d{8}-[A-Za-z0-9]{6}$");
    }

    @Test
    @DisplayName("currentTraceId() returns the most recently generated id")
    void currentTraceId_returnsLastGenerated() {
        Tracer tracer = Tracer.getInstance();
        String id = tracer.generateTraceId();
        assertThat(tracer.currentTraceId()).isEqualTo(id);
    }

    @Test
    @DisplayName("currentTraceId() returns null before any generateTraceId() call")
    void currentTraceId_returnsNullInitially() {
        assertThat(Tracer.getInstance().currentTraceId()).isNull();
    }

    @Test
    @DisplayName("generateTraceId() sets the SLF4J MDC traceId for log correlation")
    void generateTraceId_setsMdc() {
        Tracer tracer = Tracer.getInstance();
        String id = tracer.generateTraceId();
        assertThat(MDC.get("traceId")).isEqualTo(id);
    }

    // ---------- recordFailure() signature (ADR-0007 D4) ----------

    @Test
    @DisplayName("recordFailure() accepts ErrorCode + String + Optional<Path> with a path")
    void recordFailure_withPath_doesNotThrow() {
        Tracer tracer = Tracer.getInstance();
        assertThatCode(() -> tracer.recordFailure(
            ErrorCode.NETWORK_TIMEOUT,
            "test failure with screenshot",
            Optional.of(Path.of("/tmp/trace-x.png"))))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recordFailure() accepts ErrorCode + String + Optional.empty() (no screenshot)")
    void recordFailure_withoutPath_doesNotThrow() {
        Tracer tracer = Tracer.getInstance();
        assertThatCode(() -> tracer.recordFailure(
            ErrorCode.ELEMENT_NOT_FOUND,
            "test failure no screenshot",
            Optional.empty()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recordFailure() works for every ErrorCode constant")
    void recordFailure_worksForAllErrorCodes() {
        Tracer tracer = Tracer.getInstance();
        for (ErrorCode code : ErrorCode.values()) {
            assertThatCode(() -> tracer.recordFailure(code, "msg", Optional.empty()))
                .as("recordFailure should not throw for %s", code)
                .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("recordFailure() preserves the current trace_id")
    void recordFailure_preservesTraceId() {
        Tracer tracer = Tracer.getInstance();
        String id = tracer.generateTraceId();
        tracer.recordFailure(ErrorCode.NETWORK_TIMEOUT, "msg", Optional.empty());
        assertThat(tracer.currentTraceId()).isEqualTo(id);
    }

    // ---------- reset() ----------

    @Test
    @DisplayName("reset() clears the current trace_id")
    void reset_clearsTraceId() {
        Tracer tracer = Tracer.getInstance();
        tracer.generateTraceId();
        assertThat(tracer.currentTraceId()).isNotNull();

        tracer.reset();

        assertThat(tracer.currentTraceId()).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }
}
