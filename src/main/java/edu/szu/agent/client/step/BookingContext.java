package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.domain.HomeworkAttachment;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Context passed through the booking pipeline.
 *
 * <p>Per the architecture-deepening plan (改动 5, 降级路径): this class
 * services three otherwise-independent pipelines (booking / schedule /
 * homework download). The field set is grouped below by which pipeline
 * owns it. A future refactor may split this into a {@code sealed} hierarchy
 * with {@code BookingContext / ScheduleContext / HomeworkContext} and
 * {@code BookingStep<T extends BookingContext>}; for now we accept the
 * cross-pipeline field visibility in exchange for not breaking the 30+
 * files that pass {@code BookingContext} around. Each pipeline should
 * only read/write its own fields — the grouping is the contract.
 *
 * <h2>Booking pipeline (VenueBookingClient)</h2>
 * <ul>
 *   <li>{@link #request()} — input</li>
 *   <li>{@link #account()} — input</li>
 *   <li>{@link #selectedVenue()} / {@link #selectedVenue(String)} — mutable</li>
 *   <li>{@link #lastFailure()} / {@link #lastFailure(BookingResult.Failure)} — mutable</li>
 *   <li>{@link #withSelectedVenue(String)} / {@link #withLastFailure(BookingResult.Failure)} —
 *       immutable-style helpers</li>
 * </ul>
 *
 * <h2>Schedule pipeline (EhallScheduleClient)</h2>
 * <ul>
 *   <li>{@link #account()} — input</li>
 *   <li>{@link #username()} / {@link #username(String)} — input</li>
 *   <li>{@link #scheduleCourses()} / {@link #scheduleCourses(List)} — mutable</li>
 *   <li>{@link #cacheHit()} / {@link #cacheHit(boolean)} — cache status</li>
 *   <li>{@link #cacheFetchedAt()} / {@link #cacheFetchedAt(Instant)} — cache timestamp</li>
 * </ul>
 *
 * <h2>Homework pipeline (ChaoxingHomeworkClient, ChaoxingAttachmentDownloadClient)</h2>
 * <ul>
 *   <li>{@link #username()} / {@link #username(String)} — input</li>
 *   <li>{@link #homeworks()} / {@link #homeworks(List)} — mutable (list flow)</li>
 *   <li>{@link #homeworkId()} / {@link #homeworkId(String)} — input (download flow)</li>
 *   <li>{@link #attachments()} / {@link #attachments(List)} — mutable (download flow)</li>
 *   <li>{@link #outputDir()} / {@link #outputDir(Path)} — input (download flow)</li>
 * </ul>
 *
 * <h2>Cross-cutting</h2>
 * <ul>
 *   <li>{@link #sessionOk()} / {@link #sessionOk(boolean)} — set by
 *       {@code RestoreSessionStep}, read by {@code CasLoginStep} across all
 *       pipelines</li>
 * </ul>
 *
 * <p>The {@code request} and {@code account} fields are final (immutable
 * inputs set once by the caller). The mutable fields are populated by steps as
 * the pipeline progresses. {@link #withSelectedVenue(String)} and
 * {@link #withLastFailure(BookingResult.Failure)} provide an immutable-style
 * API for booking steps; homework/schedule steps mutate the relevant fields
 * directly because their pipelines are more linear.
 *
 * <p>// 编程技术: 不可变字段 + 可变步骤状态(record 风格构造 + setter 风格写入)
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class BookingContext {

    // ---------- booking pipeline ----------
    private final BookingRequest request;
    private final Account account;
    private String selectedVenue;
    private BookingResult.Failure lastFailure;

    // ---------- cross-cutting ----------
    private boolean sessionOk;
    private String username;
    /**
     * Set to {@code true} by {@link edu.szu.agent.task.BookingTask} when
     * the booking pipeline is retried with a headed browser because no
     * credential could be resolved ({@link
     * edu.szu.agent.account.AccountResolutionException}). Tells
     * {@code CasLoginStep} to skip its {@code evaluate(buildLoginScript(...))}
     * step and instead wait for the user to log in manually in the visible
     * browser window. Default {@code false}.
     *
     * @since 0.5.0
     */
    private boolean headedFallbackRequested;

    // ---------- homework pipeline ----------
    private List<Homework> homeworks;
    private String homeworkId;
    private List<HomeworkAttachment> attachments;
    private Path outputDir;

    // ---------- schedule pipeline ----------
    private List<CourseEntry> scheduleCourses;

    // ---------- cache (used by schedule + future pipelines) ----------
    /** Whether the last CacheLookupStep hit cached data. */
    private boolean cacheHit;
    /** When the cached data was fetched (populated on cache hit). */
    private Instant cacheFetchedAt;

    public BookingContext(BookingRequest request) {
        this(request, null, null, null);
    }

    public BookingContext(BookingRequest request, Account account) {
        this(request, account, null, null);
    }

    /**
     * Canonical 4-arg constructor used by the immutable-style helpers
     * {@link #withSelectedVenue(String)} and {@link #withLastFailure(BookingResult.Failure)}.
     */
    public BookingContext(BookingRequest request,
                          Account account,
                          String selectedVenue,
                          BookingResult.Failure lastFailure) {
        this.request = request;
        this.account = account;
        this.selectedVenue = selectedVenue;
        this.lastFailure = lastFailure;
    }

    public BookingContext withSelectedVenue(String selectedVenue) {
        return new BookingContext(request, account, selectedVenue, lastFailure);
    }

    public BookingContext withLastFailure(BookingResult.Failure lastFailure) {
        return new BookingContext(request, account, selectedVenue, lastFailure);
    }

    // ---------- booking pipeline accessors ----------

    public BookingRequest request() {
        return request;
    }

    public Account account() {
        return account;
    }

    public String selectedVenue() {
        return selectedVenue;
    }

    public void selectedVenue(String selectedVenue) {
        this.selectedVenue = selectedVenue;
    }

    public BookingResult lastFailure() {
        return lastFailure;
    }

    public void lastFailure(BookingResult.Failure lastFailure) {
        this.lastFailure = lastFailure;
    }

    // ---------- cross-cutting accessors ----------

    public boolean sessionOk() {
        return sessionOk;
    }

    public void sessionOk(boolean sessionOk) {
        this.sessionOk = sessionOk;
    }

    public String username() {
        return username;
    }

    public void username(String username) {
        this.username = username;
    }

    /**
     * Whether the booking pipeline is running under a headed-browser
     * fallback because no credential could be resolved.
     *
     * @return {@code true} if user must complete login manually
     * @since 0.5.0
     * @author 王子豪
     */
    public boolean headedFallbackRequested() {
        return headedFallbackRequested;
    }

    /**
     * Marks this context as running under the headed-browser fallback
     * path. Set by {@link edu.szu.agent.task.BookingTask} when it
     * rebuilds a headed browser after a credential resolution failure.
     *
     * @param headedFallbackRequested {@code true} to enable manual-login mode
     * @since 0.5.0
     * @author 王子豪
     */
    public void headedFallbackRequested(boolean headedFallbackRequested) {
        this.headedFallbackRequested = headedFallbackRequested;
    }

    // ---------- homework pipeline accessors ----------

    public List<Homework> homeworks() {
        return homeworks;
    }

    public void homeworks(List<Homework> homeworks) {
        this.homeworks = homeworks;
    }

    /**
     * Homework ID for download flow (US-008). Set by the caller before
     * pipeline runs. {@code null} for booking / list flows.
     */
    public String homeworkId() {
        return homeworkId;
    }

    public void homeworkId(String homeworkId) {
        this.homeworkId = homeworkId;
    }

    /**
     * Parsed attachment list (US-008). Populated by
     * {@code ParseAttachmentsStep} and enriched by
     * {@code DownloadFilesStep}. {@code null} for non-download flows.
     */
    public List<HomeworkAttachment> attachments() {
        return attachments;
    }

    public void attachments(List<HomeworkAttachment> attachments) {
        this.attachments = attachments;
    }

    /**
     * Local output directory for downloads (US-008). Set by the caller
     * before pipeline runs. {@code null} for non-download flows.
     */
    public Path outputDir() {
        return outputDir;
    }

    public void outputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    // ---------- schedule pipeline accessors ----------

    /**
     * Parsed schedule courses (US-009). Populated by
     * {@code ParseScheduleStep}. {@code null} for non-schedule flows.
     */
    public List<CourseEntry> scheduleCourses() {
        return scheduleCourses;
    }

    public void scheduleCourses(List<CourseEntry> scheduleCourses) {
        this.scheduleCourses = scheduleCourses;
    }

    // ---------- cache accessors ----------

    /**
     * Whether the last CacheLookupStep hit cached data.
     */
    public boolean cacheHit() {
        return cacheHit;
    }

    /**
     * Sets the cache hit flag.
     *
     * @param cacheHit {@code true} if cache was hit
     * @since 0.3.0
     * @author 王子豪
     */
    public void cacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    /**
     * When the cached data was fetched (populated on cache hit).
     */
    public Instant cacheFetchedAt() {
        return cacheFetchedAt;
    }

    /**
     * Sets the cache fetched-at timestamp.
     *
     * @param cacheFetchedAt timestamp from the cache envelope
     * @since 0.3.0
     * @author 王子豪
     */
    public void cacheFetchedAt(Instant cacheFetchedAt) {
        this.cacheFetchedAt = cacheFetchedAt;
    }

    // ---------- helpers ----------

    public BookingResult.Success success(String venueName, String confirmation) {
        return new BookingResult.Success(venueName, confirmation);
    }
}