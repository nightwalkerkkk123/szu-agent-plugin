package edu.szu.agent.domain;

/**
 * A single homework item from the LMS todo list.
 *
 * <p>Immutable value object. Used by {@link edu.szu.agent.client.ChaoxingHomeworkClient}
 * and surfaced through {@code homework list} / {@code skill homework_list}.
 *
 * // 编程技术: record(不可变值对象)
 *
 * @param homeworkId unique identifier extracted from the activity URL
 * @param courseName course name displayed in the todo item
 * @param title      homework title
 * @param deadline   deadline text in {@code YYYY.MM.DD HH:mm} format
 * @param status     status text, e.g. {@code 待提交}
 * @since 0.1.0
 * @author 王子豪
 */
public record Homework(String homeworkId, String courseName, String title,
                       String deadline, String status) {
}
