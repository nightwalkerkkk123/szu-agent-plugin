package edu.szu.agent.observability;

import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Trace id + failure recorder — singleton.
 *
 * <p>Per ADR-0007 D4: {@link #recordFailure(ErrorCode, String, Optional)}
 * is the <em>only</em> public failure-recording method. It deliberately
 * does NOT accept {@code Throwable} or {@code BookingException}:
 * <ul>
 *   <li>The retry layer catches {@code BookingException} and decides
 *       whether to re-attempt; the retry decision must not be entangled
 *       with tracing.</li>
 *   <li>The screenshot decision lives in the {@code BookingTask} (closest
 *       to the failure site), not in the tracer.</li>
 *   <li>This keeps the observability &harr; error package seam clean:
 *       {@code Tracer} imports only {@code ErrorCode} (stable enum),
 *       never {@code BookingException} (rich type with cause chains).</li>
 * </ul>
 *
 * <p>Thread-safe singleton with double-checked locking
 * (design-patterns.md §2). Generates {@code trace_id} of the form
 * {@code YYYYMMDD-XXXXXX} (8-digit date + 6-char alphanumeric suffix).
 * The id is also pushed into the SLF4J {@link MDC} under key
 * {@code "traceId"} so logback's {@code %X{traceId}} pattern picks it up
 * (see {@code src/main/resources/logback.xml}).
 *
 * <p>Programming techniques: enum-friendly metadata (ErrorCode), sealed
 * scope (no public setters), MDC integration, {@code SecureRandom} for
 * the suffix.
 *
 * // Design Pattern: Singleton (double-checked locking)
 * // 编程技术: 枚举 / record-style immutability / MDC / SecureRandom
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class Tracer {

    private static final Logger log = LoggerFactory.getLogger(Tracer.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char[] ALPHANUMERIC;
    private static final SecureRandom RNG = new SecureRandom();

    static {
        ALPHANUMERIC = new char[36];
        int idx = 0;
        for (char c = '0'; c <= '9'; c++) { ALPHANUMERIC[idx++] = c; }
        for (char c = 'A'; c <= 'Z'; c++) { ALPHANUMERIC[idx++] = c; }
    }

    /** MDC key used by logback's {@code %X{traceId}} pattern. */
    static final String MDC_TRACE_ID = "traceId";

    private static volatile Tracer instance;

    private volatile String traceId;

    private Tracer() {
    }

    /**
     * @return the process-wide singleton
     */
    public static Tracer getInstance() {
        Tracer local = instance;
        if (local == null) {
            synchronized (Tracer.class) {
                local = instance;
                if (local == null) {
                    local = new Tracer();
                    instance = local;
                }
            }
        }
        return local;
    }

    // ---------- trace_id ----------

    /**
     * Generates a fresh {@code trace_id} of the form {@code YYYYMMDD-XXXXXX}.
     * The id becomes the "current" trace id (see {@link #currentTraceId()})
     * and is also pushed into the SLF4J MDC under {@value #MDC_TRACE_ID}.
     *
     * @return the newly generated id
     */
    public String generateTraceId() {
        String date = LocalDate.now().format(DATE_FMT);
        String suffix = randomSuffix(6);
        String id = date + "-" + suffix;
        this.traceId = id;
        MDC.put(MDC_TRACE_ID, id);
        return id;
    }

    /**
     * @return the most recently generated trace id, or {@code null} if
     *         {@link #generateTraceId()} has not been called on this
     *         instance yet (or after {@link #reset()})
     */
    public String currentTraceId() {
        return traceId;
    }

    // ---------- recordFailure (ADR-0007 D4) ----------

    /**
     * Records a failure for the current trace.
     *
     * <p>Per ADR-0007 D4: signature is intentionally narrow — it takes
     * only the classifier ({@link ErrorCode}), a human message, and
     * an optional screenshot path. It does NOT accept {@code Throwable}
     * or {@code BookingException}; the caller (e.g. {@code BookingTask})
     * decides what to do with the underlying exception (log stack trace
     * elsewhere, propagate, swallow).
     *
     * <p>Behavior:
     * <ul>
     *   <li>Logs at the level matching {@code code.severity()}.</li>
     *   <li>Includes the screenshot path in the log line if present.</li>
     *   <li>Does not change the current trace id.</li>
     * </ul>
     *
     * @param code          error code (required, non-null)
     * @param message       human-readable description (required, non-null)
     * @param screenshotPath optional screenshot path; empty means no screenshot
     * @throws NullPointerException if {@code code} or {@code message} is null
     */
    public void recordFailure(ErrorCode code, String message, Optional<Path> screenshotPath) {
        Objects.requireNonNull(code, "Tracer.recordFailure.code must not be null");
        Objects.requireNonNull(message, "Tracer.recordFailure.message must not be null");
        Objects.requireNonNull(screenshotPath, "screenshotPath Optional must not be null");

        String screenshot = screenshotPath.map(p -> " screenshot=" + p).orElse("");
        String line = "trace=" + traceId
            + " code=" + code.name()
            + " severity=" + code.severity().name()
            + " retryable=" + code.isRetryable()
            + " message=" + message
            + screenshot;

        switch (code.severity()) {
            case LOW      -> log.info(line);
            case MEDIUM   -> log.warn(line);
            case HIGH     -> log.error(line);
            case CRITICAL -> log.error(line);
        }
    }

    // ---------- lifecycle ----------

    /**
     * Clears the current trace id and removes the MDC entry.
     * Intended for tests.
     */
    public void reset() {
        this.traceId = null;
        MDC.remove(MDC_TRACE_ID);
    }

    // ---------- helpers ----------

    private static String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC[RNG.nextInt(ALPHANUMERIC.length)]);
        }
        return sb.toString();
    }
}
