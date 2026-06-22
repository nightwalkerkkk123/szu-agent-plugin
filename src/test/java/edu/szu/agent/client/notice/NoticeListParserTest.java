package edu.szu.agent.client.notice;

import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NoticeListParser")
class NoticeListParserTest {

    private static final String SNIPPET = """
        <html><body>
        <fieldset><legend><a href="./infolist.asp?infotype=讲座"><strong><font>学术讲座</font></strong></a></legend>
        <table>
        <tr><td><a title="深大讲坛第224讲" href="view.asp?id=577043">深大讲坛第224讲</a></td><td>6/23 8:30</td></tr>
        <tr><td><a title="学术报告：小王子的多重宇宙" href="view.asp?id=576319">小王子讲座</a></td><td>6/22</td></tr>
        </table>
        </fieldset>
        <fieldset><legend><a href="./infolist.asp?infotype=教务"><strong><font>教务教学</font></strong></a></legend>
        <table>
        <tr><td><a title="关于毕业典礼的通知" href="view.asp?id=577097">毕业典礼通知</a></td><td>6/17</td></tr>
        </table>
        </fieldset>
        </body></html>
        """;

    @Test
    @DisplayName("解析讲座与教务通知")
    void parsesNotices() {
        List<Notice> notices = NoticeListParser.parse(SNIPPET, 2026);

        assertThat(notices).hasSize(3);
        assertThat(notices).anyMatch(n ->
            "577043".equals(n.id())
                && n.category() == NoticeCategory.LECTURE
                && n.title().contains("深大讲坛"));
        assertThat(notices).anyMatch(n ->
            "577097".equals(n.id())
                && n.category() == NoticeCategory.ANNOUNCEMENT);
    }

    @Test
    @DisplayName("讲座时间带时刻时只取日期")
    void parsesLectureDateWithoutTime() {
        List<Notice> notices = NoticeListParser.parse(SNIPPET, 2026);

        Notice lecture = notices.stream()
            .filter(n -> "577043".equals(n.id()))
            .findFirst()
            .orElseThrow();
        assertThat(lecture.publishedAt()).isEqualTo(LocalDate.of(2026, 6, 23));
        assertThat(lecture.url()).isEqualTo("https://www1.szu.edu.cn/board/view.asp?id=577043");
    }

    @Test
    @DisplayName("按发布日期倒序排序")
    void sortsByPublishedDateDescending() {
        List<Notice> notices = NoticeListParser.parse(SNIPPET, 2026);

        assertThat(notices.get(0).publishedAt()).isEqualTo(LocalDate.of(2026, 6, 23));
        assertThat(notices.get(1).publishedAt()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(notices.get(2).publishedAt()).isEqualTo(LocalDate.of(2026, 6, 17));
    }
}
