package edu.szu.agent.domain.notice;

/**
 * Category classification for SZU board (公文通) notices.
 *
 * <p>Per PRD §3.2.2 the public contract exposes four coarse categories.
 * The real board page has six sections; the parser maps them into these
 * four values for the MVP so the API surface stays stable when the
 * taxonomy is refined later.
 *
 * // 编程技术: 枚举
 *
 * @since 0.3.0
 * @author 王子豪
 */
public enum NoticeCategory {
    /** 教务教学 / 科研动态 / 党务行政. */
    ANNOUNCEMENT,
    /** 学术讲座. */
    LECTURE,
    /** 竞赛 / 活动征集. */
    COMPETITION,
    /** 学生工作 / 校园生活 / 后勤服务. */
    PUBLICITY
}
