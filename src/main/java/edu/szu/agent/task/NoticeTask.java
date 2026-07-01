package edu.szu.agent.task;

import edu.szu.agent.client.notice.NoticeListClient;
import edu.szu.agent.domain.notice.Notice;
import edu.szu.agent.domain.notice.NoticeCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * {@code notice_list} CampusTask — lists SZU board (公文通) notices.
 *
 * <p>This MVP implementation ships with a static snapshot of the board
 * list page and parses it without launching a browser.  The snapshot
 * can later be replaced by an HTTP fetch after CAS login.
 *
 * <p>Parameter contract (string keys, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code username} (required) — student ID</li>
 *   <li>{@code category} (optional) — ANNOUNCEMENT / LECTURE /
 *       COMPETITION / PUBLICITY</li>
 *   <li>{@code daysBack} (optional, default 30) — only return notices
 *       published within the last N days</li>
 *   <li>{@code page} (optional, default 1) — 1-based page number;
 *       pagination is only enabled when {@code pageSize} is also
 *       provided</li>
 *   <li>{@code pageSize} (optional, default 20, max 100) — items per
 *       page; providing this argument enables pagination</li>
 * </ul>
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.3.0
 * @author 王子豪
 */
public class NoticeTask implements CampusTask<List<Notice>> {

    private static final int DEFAULT_DAYS_BACK = 30;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE = 1;

    private final NoticeListClient client;

    public NoticeTask() {
        this(new NoticeListClient());
    }

    /**
     * Test constructor — inject a custom client.
     */
    public NoticeTask(NoticeListClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "notice_list";
    }

    @Override
    public String description() {
        return "查询深大公文通通知列表(静态 MVP)";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return TaskInputSchema.schemaWithOptional(
            "username", "学号",
            Map.of(
                "category", Map.of(
                    "type", "string",
                    "description", "可选分类过滤: ANNOUNCEMENT / LECTURE / COMPETITION / PUBLICITY"),
                "daysBack", Map.of(
                    "type", "integer",
                    "description", "查询最近 N 天,默认 30",
                    "default", 30),
                "page", Map.of(
                    "type", "integer",
                    "description", "页码(1-based,默认 1)。仅当同时传入 pageSize 时启用分页",
                    "default", 1),
                "pageSize", Map.of(
                    "type", "integer",
                    "description", "每页条数(默认 20,最大 100)。传入 pageSize 才启用分页",
                    "default", 20)
            )
        );
    }

    @Override
    public List<Notice> execute(TaskInput input) {
        input.require("username");

        final NoticeCategory categoryFilter;
        String categoryValue = input.get("category");
        if (categoryValue != null && !categoryValue.isBlank()) {
            try {
                categoryFilter = NoticeCategory.valueOf(categoryValue.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Invalid category: " + categoryValue + ". Valid: ANNOUNCEMENT, LECTURE, COMPETITION, PUBLICITY");
            }
        } else {
            categoryFilter = null;
        }

        int daysBack = input.getInt("daysBack", DEFAULT_DAYS_BACK);
        if (daysBack <= 0) {
            throw new IllegalArgumentException("daysBack must be positive");
        }

        LocalDate cutoff = LocalDate.now().minusDays(daysBack);
        List<Notice> all = client.list();

        List<Notice> filtered = all.stream()
            .filter(n -> n.publishedAt().isAfter(cutoff.minusDays(1)))
            .filter(n -> categoryFilter == null || n.category() == categoryFilter)
            .toList();

        // 分页:仅当 pageSize 显式传入时启用(与 page 配对)
        String pageSizeValue = input.get("pageSize");
        if (pageSizeValue != null && !pageSizeValue.isBlank()) {
            int pageSize = input.getInt("pageSize", DEFAULT_PAGE_SIZE);
            if (pageSize <= 0) {
                throw new IllegalArgumentException("pageSize must be positive");
            }
            if (pageSize > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException("pageSize must be <= " + MAX_PAGE_SIZE);
            }
            int page = input.getInt("page", DEFAULT_PAGE);
            if (page <= 0) {
                throw new IllegalArgumentException("page must be positive");
            }
            int from = Math.min((page - 1) * pageSize, filtered.size());
            int to = Math.min(from + pageSize, filtered.size());
            return filtered.subList(from, to);
        }

        return filtered;
    }
}