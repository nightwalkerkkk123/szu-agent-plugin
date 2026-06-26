package edu.szu.agent.task;

import edu.szu.agent.knowledge.KnowledgeCategory;
import edu.szu.agent.knowledge.KnowledgeRepository;
import edu.szu.agent.knowledge.KnowledgeResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code kb_query} CampusTask — answers SZU knowledge-base queries.
 *
 * <p>Parameters (string contract, matches MCP {@code inputSchema}):
 * <ul>
 *   <li>{@code query} (required) — user question / keywords
 *   <li>{@code limit} (optional, default 5) — max number of results
 *   <li>{@code category} (optional) — CAMPUS_BASICS / DINING / LIBRARY /
 *       ACADEMICS / FAQ
 * </ul>
 *
 * // 编程技术: 泛型 / 枚举 / Lambda
 *
 * @since 0.2.0
 * @author 王子豪
 */
public class KnowledgeTask implements CampusTask<List<KnowledgeResult>> {

    private final KnowledgeRepository repository;

    public KnowledgeTask() {
        this(new KnowledgeRepository());
    }

    /**
     * Test constructor — inject a custom repository.
     */
    public KnowledgeTask(KnowledgeRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "kb_query";
    }

    @Override
    public String description() {
        return """
            查询深大校园知识库(校园基础、餐饮、图书馆、学业、FAQ 五大类),用关键词匹配返回相关条目。
            重要约束(必须遵守,否则调用会失败或返回空):
            1. query 是必填,支持中文/英文混合关键词,例如 "图书馆"、"食堂"、"选课"、"校园卡"。
               多个关键词用空格或半角逗号分隔,例如 "图书馆 开馆时间"。
            2. 匹配策略: 优先 Exact(整词命中),回退 Contains(子串),回退 Regex(默认关闭)。
               简而言之 query="图书馆" 会精确命中所有含"图书馆"的条目,不必担心歧义。
            3. category 可选,枚举值固定 5 个: CAMPUS_BASICS(校园基础) / DINING(餐饮服务) /
               LIBRARY(图书馆) / ACADEMICS(学业选课) / FAQ(常见问题)。传错(如小写 "library")会抛
               IllegalArgumentException。LLM 必须严格使用大写下划线形式。
            4. limit 可选,默认 5。传 0 或负数抛 IllegalArgumentException("limit must be positive")。
            5. 返回顺序按相关度(score)降序,score 范围 0.0-1.0。不保证覆盖所有匹配项,若用户需要
               全部结果应传 limit=50 并提示用户进一步筛选。
            6. 这是纯静态查询,不需要凭证、不需要浏览器、不会发起任何 IO。
            7. 适合回答"图书馆几点关门?"、"食堂可以用支付宝吗?"、"选课什么时候开始?"等问题。
            """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> query = TaskInputSchema.property("string",
            "查询关键词,中文或英文,例如 '图书馆'、'食堂'、'选课'。必填。",
            Map.of("minLength", 1, "examples", List.of("图书馆", "食堂", "选课 退课")));

        Map<String, Object> limit = TaskInputSchema.property("integer",
            "最大返回条数,默认 5。必须 > 0。",
            Map.of("default", 5, "minimum", 1, "examples", List.of(5, 10)));

        Map<String, Object> category = TaskInputSchema.enumProperty(
            "可选分类过滤,默认不过滤。",
            List.of("CAMPUS_BASICS", "DINING", "LIBRARY", "ACADEMICS", "FAQ"),
            Map.of("examples", List.of("LIBRARY")));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", query);
        properties.put("limit", limit);
        properties.put("category", category);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public ToolAnnotations annotations() {
        Map<String, Object> ex1 = new LinkedHashMap<>();
        ex1.put("query", "图书馆");

        Map<String, Object> ex2 = new LinkedHashMap<>();
        ex2.put("query", "食堂");
        ex2.put("category", "DINING");

        Map<String, Object> ex3 = new LinkedHashMap<>();
        ex3.put("query", "选课");
        ex3.put("limit", 10);

        Map<String, Object> ex4 = new LinkedHashMap<>();
        ex4.put("query", "校园卡");
        ex4.put("category", "CAMPUS_BASICS");

        Map<String, Object> ex5 = new LinkedHashMap<>();
        ex5.put("query", "图书馆 开馆时间");
        ex5.put("category", "LIBRARY");
        ex5.put("limit", 3);

        Map<String, Object> ex6 = new LinkedHashMap<>();
        ex6.put("query", "FAQ 校园网");

        Map<String, Object> ex7 = new LinkedHashMap<>();
        ex7.put("query", "ACADEMICS 选课");
        ex7.put("category", "ACADEMICS");
        ex7.put("limit", 20);

        return ToolAnnotations.builder()
            .example(ex1)
            .example(ex2)
            .example(ex3)
            .example(ex4)
            .example(ex5)
            .example(ex6)
            .example(ex7)
            .resultShape("""
                List<KnowledgeResult>:
                - snippet: 命中的正文片段
                - sourcePath: classpath 内源 Markdown 路径,例如 "knowledge/03-library.md"
                - relevanceScore: 1.0=标题精确命中,0.9=正文精确命中,0.7=子串命中,0.5=正则命中""")
            .commonError("category 传 \"library\"(小写)→ INVALID_REQUEST;必须用大写 \"LIBRARY\"")
            .commonError("query=\"\"(空字符串)→ INVALID_REQUEST(\"query\" missing/blank);必须非空")
            .commonError("limit=0 或负数 → INVALID_REQUEST(\"limit must be positive\")")
            .build();
    }

    @Override
    public List<KnowledgeResult> execute(TaskInput input) {
        String query = input.require("query");
        int limit = input.getInt("limit", 5);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        KnowledgeCategory category = null;
        String categoryValue = input.get("category");
        if (categoryValue != null && !categoryValue.isBlank()) {
            category = KnowledgeCategory.valueOf(categoryValue.trim());
        }

        return repository.query(query, limit, category);
    }
}
