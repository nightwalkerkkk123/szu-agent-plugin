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
 * Mutable context passed through the booking pipeline.
 *
 * <p>Each step reads from {@link #request} and writes intermediate results
 * here (e.g. {@link #selectedVenue}). The {@link #account} field holds
 * resolved credentials injected by the caller before the pipeline runs.
 * This keeps {@link BookingRequest} free of credential types.
 *
 * // 编程技术: record(不可变外壳) / Lambda
 *
 * @since 0.1.0
 * @author 王子豪
 */
public final class BookingContext {

    private final BookingRequest request;
    private final Account account;
    private String selectedVenue;
    private BookingResult lastFailure;
    private List<Homework> homeworks;
    private boolean sessionOk;
    private String username;
    private String homeworkId;
    private List<HomeworkAttachment> attachments;
    private Path outputDir;
    private List<CourseEntry> scheduleCourses;

    public BookingContext(BookingRequest request, Account account) {
        this.request = request;
        this.account = account;
    }

    /**
     * Convenience constructor for steps that don't need credentials
     * (e.g. {@link SelectCampusStep}, {@link SelectSportStep},
     * {@link SelectTimeSlotStep}, {@link SelectVenueStep},
     * {@link ConfirmBookingStep}). Equivalent to passing {@code null}
     * for {@code account}.
     */
    public BookingContext(BookingRequest request) {
        this(request, null);
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