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
        NoticeTask task = new NoticeTask(new NoticeListClient("<html></html>", 2026));
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
        NoticeTask task = new NoticeTask(new NoticeListClient(html, today.getYear()));

        List<Notice> lectures = task.execute(new TaskInput(Map.of(
            "username", "u",
            "category", "LECTURE")));

        assertThat(lectures).hasSize(1);
        assertThat(lectures.get(0).category()).isEqualTo(NoticeCategory.LECTURE);
    }

    @Test
    @DisplayName("无效分类抛异常")
    void rejectsInvalidCategory() {
        NoticeTask task = new NoticeTask(new NoticeListClient("<html></html>", 2026));
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
        NoticeTask task = new NoticeTask(new NoticeListClient(html, today.getYear()));

        List<Notice> notices = task.execute(new TaskInput(Map.of(
            "username", "u",
            "daysBack", "7")));

        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).title()).contains("深大讲坛");
    }
}
