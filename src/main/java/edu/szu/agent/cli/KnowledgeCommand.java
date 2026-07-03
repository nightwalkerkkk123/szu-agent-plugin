package edu.szu.agent.cli;

import edu.szu.agent.knowledge.KnowledgeResult;
import edu.szu.agent.task.KnowledgeTask;
import edu.szu.agent.task.TaskInput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code kb} subcommand — knowledge-base query interface.
 *
 * <p>Usage:
 * <pre>{@code
 * szu-agent kb query --query "图书馆"
 * szu-agent kb query --query "食堂" --limit 3 --category DINING
 * }</pre>
 *
 * // 编程技术: 注解 / Lambda
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "kb",
    description = "深大知识库查询",
    mixinStandardHelpOptions = true,
    subcommands = {
        KnowledgeCommand.QueryAction.class
    }
)
public class KnowledgeCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    @Command(
        name = "query",
        description = "Query the SZU knowledge base"
    )
    public static class QueryAction implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", arity = "0..1", description = "Query string")
        private String positionalQuery;

        @Option(names = {"-q", "--query"}, description = "Query string")
        private String optionQuery;

        @Option(names = {"-l", "--limit"}, description = "Max number of results", defaultValue = "5")
        private int limit;

        @Option(names = {"-c", "--category"}, description = "Optional category: CAMPUS_BASICS / DINING / LIBRARY / ACADEMICS / FAQ")
        private String category;

        @Option(names = {"-f", "--format"}, description = "Output format: json or human", defaultValue = "human")
        private String format;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            String query = positionalQuery != null && !positionalQuery.isBlank()
                ? positionalQuery
                : optionQuery;
            if (query == null || query.isBlank()) {
                out.println("Missing required option: --query");
                return 2;
            }

            Map<String, String> params = new LinkedHashMap<>();
            params.put("query", query);
            params.put("limit", String.valueOf(limit));
            if (category != null && !category.isBlank()) {
                params.put("category", category);
            }

            List<KnowledgeResult> results = new KnowledgeTask().execute(new TaskInput(params));

            if ("json".equalsIgnoreCase(format)) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("count", results.size());
                data.put("results", results.stream()
                    .map(r -> Map.of(
                        "snippet", r.snippet(),
                        "sourcePath", r.sourcePath(),
                        "relevanceScore", r.relevanceScore()))
                    .toList());
                out.println(SkillCommand.toJson(data));
            } else {
                out.println("Results (" + results.size() + "):");
                for (KnowledgeResult r : results) {
                    out.printf("  [%.2f] %s%n    %s%n%n",
                        r.relevanceScore(), r.sourcePath(), r.snippet());
                }
            }
            return 0;
        }
    }
}
