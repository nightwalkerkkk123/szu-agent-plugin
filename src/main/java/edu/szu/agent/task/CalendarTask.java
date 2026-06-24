package edu.szu.agent.task;

import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.AcademicEventType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code calendar_get} CampusTask — returns the SZU academic calendar.
 *
 * <p>This MVP implementation ships with the static 2025-2026 second
 * semester calendar parsed from the official SZU academic calendar
 * document.  No browser is launched; the data is embedded so the Skill
 * is always available even when the public page is unreachable.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code academicYear} (optional) — e.g. "2025-2026"; defaults to
 *       the current academic year inferred from the system date</li>
 * </ul>
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.3.0
 * @author 王子豪
 */
public class CalendarTask implements CampusTask<List<AcademicEvent>> {

    private static final String DEFAULT_YEAR = "2025-2026";
    private static final String SEMESTER_TAG = "2025-2026-SPRING";

    @Override
    public String name() {
        return "calendar_get";
    }

    @Override
    public String description() {
        return "查询深大校历";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return TaskInputSchema.optionalOnly(
            Map.of("academicYear", Map.of(
                "type", "string",
                "description", "学年,例如 2025-2026"))
        );
    }

    @Override
    public List<AcademicEvent> execute(TaskInput input) {
        String year = input.get("academicYear");
        if (year == null || year.isBlank()) {
            year = defaultAcademicYear();
        }
        if (!DEFAULT_YEAR.equals(year)) {
            // MVP: only the 2025-2026 calendar is embedded.
            return List.of();
        }
        return spring2026Events();
    }

    /**
     * Infers the current academic year from the system date.  Between
     * January and July the current year is the spring semester of the
     * previous/fall year; between August and December it is the fall
     * semester of the current year.
     */
    public static String defaultAcademicYear() {
        LocalDate now = LocalDate.now();
        if (now.getMonthValue() <= 7) {
            return (now.getYear() - 1) + "-" + now.getYear();
        }
        return now.getYear() + "-" + (now.getYear() + 1);
    }

    /**
     * Static events parsed from 《深圳大学 2025-2026 学年第二学期校历说明》.
     */
    static List<AcademicEvent> spring2026Events() {
        List<AcademicEvent> events = new ArrayList<>();

        // 学期整体起止
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 4),
            AcademicEventType.SEMESTER_START,
            "2025-2026学年第二学期起：3月4日至7月17日",
            SEMESTER_TAG));

        // 教职工报到
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 4),
            AcademicEventType.SEMESTER_START,
            "全体教职工上班报到（星期三）",
            SEMESTER_TAG));

        // 学生报到注册
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 5),
            AcademicEventType.SEMESTER_START,
            "学生报到、注册（星期四）；开学时间",
            SEMESTER_TAG));

        // 缓考 / 补考 3月4日 ~ 3月8日
        for (int day = 4; day <= 8; day++) {
            events.add(AcademicEvent.of(
                LocalDate.of(2026, 3, day),
                AcademicEventType.EXAM_WEEK,
                "本科生缓考、补考；研究生缓考",
                SEMESTER_TAG));
        }

        // 开始上课
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            AcademicEventType.SEMESTER_START,
            "开始上课（星期一）",
            SEMESTER_TAG));

        // 本科生上课周数 1-17 周：3月9日 ~ 7月3日
        // 仅在关键节点保留一个标记事件，避免每日展开淹没输出
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            AcademicEventType.SEMESTER_START,
            "本科生上课周数：第一至十七周（3月9日~7月3日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 7, 3),
            AcademicEventType.SEMESTER_START,
            "本科生第十七周上课结束",
            SEMESTER_TAG));

        // 研究生上课周数 1-12 周：3月9日 ~ 5月29日
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            AcademicEventType.SEMESTER_START,
            "研究生部分课程上课周数：第一至十二周（3月9日~5月29日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 5, 29),
            AcademicEventType.SEMESTER_START,
            "研究生部分课程第十二周上课结束",
            SEMESTER_TAG));

        // 研究生上课周数 1-16 周：3月9日 ~ 6月26日
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            AcademicEventType.SEMESTER_START,
            "研究生部分课程上课周数：第一至十六周（3月9日~6月26日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 6, 26),
            AcademicEventType.SEMESTER_START,
            "研究生部分课程第十六周上课结束",
            SEMESTER_TAG));

        // 本科生期末考试 第十八、十九周：7月6日 ~ 7月17日
        for (int day = 6; day <= 17; day++) {
            events.add(AcademicEvent.of(
                LocalDate.of(2026, 7, day),
                AcademicEventType.EXAM_WEEK,
                "本科生期末考试（第十八、十九周）",
                SEMESTER_TAG));
        }

        // 研究生期末考试：课程结束两周内进行（以 6月27日为起点两周示例）
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 6, 27),
            AcademicEventType.EXAM_WEEK,
            "研究生期末考试：课程结束两周内进行（示例起始日）",
            SEMESTER_TAG));

        // 毕业班安排
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 3, 9),
            AcademicEventType.SEMESTER_START,
            "本科生毕业论文及答辩：第1~10周（3月9日~5月14日）",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 5, 15),
            AcademicEventType.SEMESTER_START,
            "研究生学位论文答辩：第10周以前完成（5月15日前）",
            SEMESTER_TAG));

        // 毕业典礼
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 6, 26),
            AcademicEventType.HOLIDAY,
            "毕业典礼安排",
            SEMESTER_TAG));

        // 暑假
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 7, 18),
            AcademicEventType.BREAK,
            "暑假开始：7月18日~8月27日",
            SEMESTER_TAG));
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 8, 27),
            AcademicEventType.BREAK,
            "暑假结束",
            SEMESTER_TAG));

        // 其他提示性事件
        events.add(AcademicEvent.of(
            LocalDate.of(2026, 5, 1),
            AcademicEventType.HOLIDAY,
            "五一社会实践周、法定休假日等具体安排另行通知",
            SEMESTER_TAG));

        return List.copyOf(events);
    }
}
