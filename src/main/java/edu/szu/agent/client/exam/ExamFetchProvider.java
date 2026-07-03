package edu.szu.agent.client.exam;

import edu.szu.agent.domain.exam.ExamSchedule;
import edu.szu.agent.error.ExamListException;

import java.util.List;

/**
 * Functional interface for fetching the exam schedule HTML from the
 * SZU ehall exam page (requires CAS login).
 *
 * <p>// Design Pattern: Strategy
 * <p>// 编程技术: 函数式接口 / Lambda / 泛型
 *
 * @since 0.6.0
 * @author 王子豪
 */
@FunctionalInterface
public interface ExamFetchProvider {

    /**
     * Fetch the raw HTML of the exam schedule page from ehall.
     *
     * @throws ExamListException if the fetch fails (network, timeout, selector mismatch)
     * @return the full HTML content of the page
     */
    String fetchHtml();

    /**
     * Default implementation: fetch HTML, then parse it with {@link ExamListParser}.
     *
     * @return the parsed list of {@link ExamSchedule}
     */
    default List<ExamSchedule> fetchAndParse() {
        String html = fetchHtml();
        return ExamListParser.parse(html, java.time.LocalDate.now().getYear());
    }
}
