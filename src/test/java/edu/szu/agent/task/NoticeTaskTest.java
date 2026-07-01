package edu.szu.agent.task;

import edu.szu.agent.client.notice.NoticeListClient;
import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeCategory;
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

    @Test
    @DisplayName("name = notice_list")
    void nameAndDescriptionAreCorrect() {
        NoticeTask task = new NoticeTask();
        assertThat(task.name()).isEqualTo("notice_list");
        assertThat(task.description()).isEqualTo("查询深大公文通通知列表(静态 MVP)");
    }

    @Test
    @DisplayName("requires username")
    void requiresUsername() {
        NoticeTask task = new NoticeListClientAwareTask("<html></html>", 2026);
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
        NoticeTask task = new NoticeListClientAwareTask(html, today.getYear());

        List<Notice> lectures = task.execute(new TaskInput(Map.of(
            "username", "u",
            "category", "LECTURE")));

        assertThat(lectures).hasSize(1);
        assertThat(lectures.get(0).category()).isEqualTo(NoticeCategory.LECTURE);
    }

    @Test
    @DisplayName("无效分类抛异常")
    void rejectsInvalidCategory() {
        NoticeTask task = new NoticeListClientAwareTask("<html></html>", 2026);
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
        NoticeTask task = new NoticeListClientAwareTask(html, today.getYear());

        List<Notice> notices = task.execute(new TaskInput(Map.of(
            "username", "u",
            "daysBack", "7")));

        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).title()).contains("深大讲坛");
    }

    private static final String PAGINATION_SNAPSHOT = """
        <html><body>
        <fieldset><legend><a href="./infolist.asp?infotype=讲座"><strong><font>学术讲座</font></strong></a></legend>
        <table>
        %s
        </table>
        </fieldset>
        </body></html>
        """;

    private static String row(String id, String title, String date) {
        return "<tr><td><a title=\"" + title + "\" href=\"view.asp?id=" + id + "\">" + title + "</a></td><td>" + date + "</td></tr>";
    }

    @Test
    @DisplayName("分页：page+pageSize 切片")
    void paginatesByPageAndPageSize() {
        LocalDate today = LocalDate.now();
        String todayStr = today.getMonthValue() + "/" + today.getDayOfMonth();
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            rows.append(row(String.valueOf(577000 + i), "讲座" + i, todayStr)).append("\n");
        }
        String html = PAGINATION_SNAPSHOT.formatted(rows);
        NoticeTask task = new NoticeListClientAwareTask(html, today.getYear());

        List<Notice> page1 = task.execute(new TaskInput(Map.of(
            "username", "u",
            "page", "1",
            "pageSize", "10")));

        List<Notice> page2 = task.execute(new TaskInput(Map.of(
            "username", "u",
            "page", "2",
            "pageSize", "10")));

        List<Notice> page3 = task.execute(new TaskInput(Map.of(
            "username", "u",
            "page", "3",
            "pageSize", "10")));

        assertThat(page1).hasSize(10);
        assertThat(page2).hasSize(10);
        assertThat(page3).hasSize(5);
        // 不重叠
        assertThat(page1).noneMatch(n -> page2.contains(n));
        assertThat(page2).noneMatch(n -> page3.contains(n));
    }

    @Test
    @DisplayName("分页：pageSize <= 0 抛异常")
    void rejectsNonPositivePageSize() {
        NoticeTask task = new NoticeListClientAwareTask("<html></html>", 2026);
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of(
            "username", "u",
            "page", "1",
            "pageSize", "0"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pageSize");
    }

    @Test
    @DisplayName("分页：page <= 0 抛异常")
    void rejectsNonPositivePage() {
        NoticeTask task = new NoticeListClientAwareTask("<html></html>", 2026);
        assertThatThrownBy(() -> task.execute(new TaskInput(Map.of(
            "username", "u",
            "page", "0",
            "pageSize", "10"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("page");
    }

    @Test
    @DisplayName("分页：仅给 page 不给 pageSize 视为不启用，返回全部")
    void pageWithoutPageSizeReturnsAll() {
        LocalDate today = LocalDate.now();
        String todayStr = today.getMonthValue() + "/" + today.getDayOfMonth();
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            rows.append(row(String.valueOf(577000 + i), "讲座" + i, todayStr)).append("\n");
        }
        String html = PAGINATION_SNAPSHOT.formatted(rows);
        NoticeTask task = new NoticeListClientAwareTask(html, today.getYear());

        List<Notice> all = task.execute(new TaskInput(Map.of(
            "username", "u",
            "page", "2")));

        assertThat(all).hasSize(5);
    }

    /**
     * Local helper wrapping the (package-private) two-arg NoticeListClient constructor
     * used by tests in the same module.  Avoids relying on test-package visibility.
     */
    private static final class NoticeListClientAwareTask extends NoticeTask {
        NoticeListClientAwareTask(String html, int year) {
            super(new NoticeListClient(html, year));
        }
    }
}