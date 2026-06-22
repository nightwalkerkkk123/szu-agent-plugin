package edu.szu.agent.client.exam;

import edu.szu.agent.domain.exam.ExamSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExamListParser")
class ExamListParserTest {

    @Test
    @DisplayName("parses valid exam table rows")
    void parsesValidRows() {
        String html = """
            <html><body>
            <table>
            <tbody>
                <tr>
                    <td>7月14日</td>
                    <td>星期二</td>
                    <td>操作系统</td>
                    <td>[1500110002]</td>
                    <td>2026年07月14日 09:00-11:00</td>
                    <td>致理楼L1-601</td>
                    <td>杜智华</td>
                </tr>
            </tbody>
            </table>
            </body></html>
            """;

        List<ExamSchedule> results = ExamListParser.parse(html, 2026);

        assertThat(results).hasSize(1);
        ExamSchedule exam = results.get(0);
        assertThat(exam.date()).isEqualTo("7月14日");
        assertThat(exam.weekday()).isEqualTo("星期二");
        assertThat(exam.courseName()).isEqualTo("操作系统");
        assertThat(exam.courseCode()).isEqualTo("1500110002");
        assertThat(exam.examDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(exam.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(exam.endTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(exam.venue()).isEqualTo("致理楼L1-601");
        assertThat(exam.invigilator()).isEqualTo("杜智华");
    }

    @Test
    @DisplayName("parses multiple exam rows")
    void parsesMultipleRows() {
        String html = """
            <html><body>
            <table>
            <tbody>
                <tr>
                    <td>7月14日</td>
                    <td>星期二</td>
                    <td>操作系统</td>
                    <td>[1500110002]</td>
                    <td>2026年07月14日 09:00-11:00</td>
                    <td>致理楼L1-601</td>
                    <td>杜智华</td>
                </tr>
                <tr>
                    <td>7月7日</td>
                    <td>星期二</td>
                    <td>多媒体系统导论</td>
                    <td>[1502860001]</td>
                    <td>2026年07月07日 14:30-16:30</td>
                    <td>致理楼L3-404</td>
                    <td>方山城</td>
                </tr>
            </tbody>
            </table>
            </body></html>
            """;

        List<ExamSchedule> results = ExamListParser.parse(html, 2026);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).courseName()).isEqualTo("操作系统");
        assertThat(results.get(1).courseName()).isEqualTo("多媒体系统导论");
    }

    @Test
    @DisplayName("uses defaultYear when exam time omits year")
    void usesDefaultYearWhenExamTimeOmitsYear() {
        String html = """
            <html><body>
            <table>
            <tbody>
                <tr>
                    <td>7月14日</td>
                    <td>星期二</td>
                    <td>操作系统</td>
                    <td>[1500110002]</td>
                    <td>07月14日 09:00-11:00</td>
                    <td>致理楼L1-601</td>
                    <td>杜智华</td>
                </tr>
            </tbody>
            </table>
            </body></html>
            """;

        List<ExamSchedule> results = ExamListParser.parse(html, 2026);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).examDate()).isEqualTo(LocalDate.of(2026, 7, 14));
    }

    @Test
    @DisplayName("returns empty list for empty HTML")
    void returnsEmptyForNoMatches() {
        String html = "<html><body><p>No exams here</p></body></html>";
        List<ExamSchedule> results = ExamListParser.parse(html, 2026);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("handles whitespace in cells")
    void handlesWhitespace() {
        String html = """
            <html><body>
            <table>
            <tbody>
                <tr>
                    <td>  7月14日  </td>
                    <td>  星期二  </td>
                    <td>  操作系统  </td>
                    <td>  [1500110002]  </td>
                    <td>  2026年07月14日 09:00-11:00  </td>
                    <td>  致理楼L1-601  </td>
                    <td>  杜智华  </td>
                </tr>
            </tbody>
            </table>
            </body></html>
            """;

        List<ExamSchedule> results = ExamListParser.parse(html, 2026);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).courseName()).isEqualTo("操作系统");
    }

    @Test
    @DisplayName("extracts course code from brackets")
    void extractsCourseCode() {
        String html = """
            <html><body>
            <table>
            <tbody>
                <tr>
                    <td>7月14日</td>
                    <td>星期二</td>
                    <td>操作系统</td>
                    <td>[1500110002]</td>
                    <td>2026年07月14日 09:00-11:00</td>
                    <td>致理楼L1-601</td>
                    <td>杜智华</td>
                </tr>
            </tbody>
            </table>
            </body></html>
            """;

        List<ExamSchedule> results = ExamListParser.parse(html, 2026);

        assertThat(results.get(0).courseCode()).isEqualTo("1500110002");
    }

    @Test
    @DisplayName("parses afternoon exam time correctly")
    void parsesAfternoonTime() {
        String html = """
            <html><body>
            <table>
            <tbody>
                <tr>
                    <td>7月7日</td>
                    <td>星期二</td>
                    <td>多媒体系统导论</td>
                    <td>[1502860001]</td>
                    <td>2026年07月07日 14:30-16:30</td>
                    <td>致理楼L3-404</td>
                    <td>方山城</td>
                </tr>
            </tbody>
            </table>
            </body></html>
            """;

        List<ExamSchedule> results = ExamListParser.parse(html, 2026);

        assertThat(results.get(0).startTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(results.get(0).endTime()).isEqualTo(LocalTime.of(16, 30));
    }
}