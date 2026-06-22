package edu.szu.agent.client.step;

import edu.szu.agent.account.Account;
import edu.szu.agent.domain.BookingRequest;
import edu.szu.agent.domain.BookingResult;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.Homework;
import edu.szu.agent.domain.HomeworkAttachment;

import java.nio.file.Path;
import java.util.List;

/**
 * Context passed through the booking pipeline.
 *
 * <p>The {@code request} and {@code account} fields are final (immutable
 * inputs set once by the caller). The mutable fields ({@code selectedVenue},
 * {@code lastFailure}, {@code homeworks}, etc.) are populated by steps as
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

    private final BookingRequest request;
    private final Account account;
    private String selectedVenue;
    private BookingResult.Failure lastFailure;
    private List<Homework> homeworks;
    private boolean sessionOk;
    private String username;
    private String homeworkId;
    private List<HomeworkAttachment> attachments;
    private Path outputDir;
    private List<CourseEntry> scheduleCourses;

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

    public List<Homework> homeworks() {
        return homeworks;
    }

    public void homeworks(List<Homework> homeworks) {
        this.homeworks = homeworks;
    }

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

    public BookingResult.Success success(String venueName, String confirmation) {
        return new BookingResult.Success(venueName, confirmation);
    }
}