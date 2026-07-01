package edu.szu.agent.task;

import edu.szu.agent.client.notice.NoticeListClient;
import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeCategory;
import edu.szu.agent.domain.notice.NoticeListResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NoticeTask")
class NoticeTaskTest {

    private static final String SNAPSHOT = """
        <html><body>
        <fieldset><legend><a href="./infolist.asp?infotype=讲座"><strong><font>学术讲座</font></strong></a></legend>
        <table>
        <tr><td><a title="深大讲坛第224讲" href="view.asp?id=577043">深大讲坛第224讲</a></td><td>%s 8:30</td></tr>
        </table>
        </fieldset>
        <fieldset><legend><a href="./infolist.asp?infotype=教务"><strong><font>教务教学</font></strong></a></legend>
        <table>
        <tr><td><a title="关于毕业典礼的通知" href="view.asp?id=577097">毕业典礼通知</a></td><td>%s</td></tr>
        </table>
        </fieldset>
        </body></html>
        """;

    /** Helper — unwraps the sealed Success variant for assertions. */
    private static List<Notice> unwrap(NoticeListResult result) {
        assertThat(result).isInstanceOf(NoticeListResult.Success.class);
        return ((NoticeListResult.Success) result).notices();
    }

    @Test
    @DisplayName("name = notice_list")
    void nameAndDescriptionAreCorrect() {
        NoticeTask task = new NoticeTask();
        assertThat(task.name()).isEqualTo("notice_list");
        assertThat(task.description())
            .startsWith("查询深圳大学公文通通知列表")
            .contains("ANNOUNCEMENT", "LECTURE", "daysBack");
    }

    @Test
    @DisplayName("requires username")
    void requiresUsername() {
        NoticeTask task = newNoticeTaskWithStaticHtml("<html></html>", 2026);
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of("daysBack", "7"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username");
    }

    @Test
    @DisplayName("按分类过滤")
    void filtersByCategory() {
        LocalDate today = LocalDate.now();
        String html = SNAPSHOT.formatted(
            today.getMonthValue() + "/" + today.getDayOfMonth(),
            today.getMonthValue() + "/" + today.getDayOfMonth());
        NoticeTask task = newNoticeTaskWithStaticHtml(html, today.getYear());

        NoticeListResult result = task.execute(new TaskInput(Map.of(
            "username", "u",
            "category", "LECTURE")));

        List<Notice> lectures = unwrap(result);
        assertThat(lectures).hasSize(1);
        assertThat(lectures.get(0).category()).isEqualTo(NoticeCategory.LECTURE);
    }

    @Test
    @DisplayName("无效分类抛异常")
    void rejectsInvalidCategory() {
        NoticeTask task = newNoticeTaskWithStaticHtml("<html></html>", 2026);
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of(
            "username", "u",
            "category", "FOOBAR"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid category");
    }

    @Test
    @DisplayName("daysBack 过滤旧通知")
    void filtersByDaysBack() {
        LocalDate today = LocalDate.now();
        String html = SNAPSHOT.formatted(
            today.getMonthValue() + "/" + today.getDayOfMonth(),
            "1/1");
        NoticeTask task = newNoticeTaskWithStaticHtml(html, today.getYear());

        NoticeListResult result = task.execute(new TaskInput(Map.of(
            "username", "u",
            "daysBack", "7")));

        List<Notice> notices = unwrap(result);
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).title()).contains("深大讲坛");
    }

    /**
     * Build a static-only NoticeTask that reads the supplied HTML as the
     * embedded snapshot. Bypasses the {@code @Deprecated} client ctor
     * (which silently ignores its argument) by going through the 3-arg
     * test ctor that takes a fallback supplier.
     */
    private static NoticeTask newNoticeTaskWithStaticHtml(String html, int year) {
        return new NoticeTask(
            () -> {
                throw new IllegalStateException("test: real path should not run");
            },
            () -> new NoticeListClient(html, year),
            true);
    }
}
