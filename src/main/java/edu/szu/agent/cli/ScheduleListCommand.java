package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.EhallScheduleClient;
import edu.szu.agent.config.ConfigManager;
import edu.szu.agent.domain.CourseEntry;
import edu.szu.agent.domain.ScheduleListResult;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.observability.Tracer;
import edu.szu.agent.retry.RetryPolicies;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code schedule list} subcommand — lists all course entries from the ehall
 * schedule grid for the current semester.
 *
 * <p>Output JSON schema:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": {
 *     "snapshotAt": "2026-06-18T10:00:00+08:00",
 *     "count": 4,
 *     "courses": [
 *       {
 *         "courseName": "操作系统",
 *         "section": "05",
 *         "teacher": "杜智华",
 *         "room": "致理楼L1-601",
 *         "weekday": 3,
 *         "weekdayName": "星期三",
 *         "beginUnit": 1,
 *         "endUnit": 2,
 *         "startTime": "08:00",
 *         "endTime": "09:50",
 *         "weekRange": "1-17",
 *         "weeks": [1, 2, 3, "..."],
 *         "isAdjusted": false
 *       }
 *     ]
 *   },
 *   "errorCode": null,
 *   "errorMessage": null,
 *   "traceId": "20260618-ABC123",
 *   "elapsedMs": 4321
 * }
 * }</pre>
 *
 * // 编程技术: 注解 / 枚举 / Lambda / record
 *
 * @since 0.1.0
 * @author 王子豪
 */
@Command(
    name = "list",
    description = "List schedule courses from SZU ehall",
    mixinStandardHelpOptions = true
)
public class ScheduleListCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter SNAPSHOT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID (e.g. 2023150090)")
    private String username;

    @Option(names = {"-f", "--format"}, description = "Output format: json or human", defaultValue = "json")
    private String format;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for credentials")
    private String envFile;

    @Option(names = {"--dry-run"}, description = "Dry-run mode (unit test fixture only)")
    private boolean dryRun;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        try {
            if (dryRun) {
                long elapsed = System.currentTimeMillis() - startMs;
                out.println(CommandOutput.formatResult(true, buildDryRunData(),
                    null, null, traceId, elapsed, format));
                return 0;
            }

            if (envFile != null) {
                Path envPath = Path.of(envFile);
                if (!Files.exists(envPath)) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    out.println(CommandOutput.formatResult(false, null,
                        "INVALID_REQUEST", "env file not found: " + envFile,
                        traceId, elapsed, format));
                    return 3;
                }
                ConfigManager.getInstance().loadEnvFile(envPath);
            }

            Map<String, String> effectiveEnv = new LinkedHashMap<>(System.getenv());
            if (envFile != null) {
                effectiveEnv.putAll(ConfigManager.getInstance().envFileProps());
            }

            Account account = AccountResolver.resolve(username, effectiveEnv);

            ConfigManager.getInstance().load();
            EhallScheduleClient client = new EhallScheduleClient(
                account,
                ConfigManager.getInstance().browser(),
                RetryPolicies.defaultBooking());

            ScheduleListResult result = client.list();

            long elapsed = System.currentTimeMillis() - startMs;
            return formatAndOutput(out, result, traceId, elapsed);
        } catch (AccountResolutionException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                "CREDENTIAL_NOT_FOUND", e.getMessage(), traceId, elapsed, format));
            return 3;
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, format));
            return CommandOutput.exitCodeFor(e.code());
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                "INVALID_REQUEST", e.getMessage(), traceId, elapsed, format));
            return 2;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                "UNKNOWN", e.getMessage(), traceId, elapsed, format));
            return 1;
        }
    }

    private int formatAndOutput(PrintWriter out, ScheduleListResult result,
                                String traceId, long elapsedMs) {
        if (result instanceof ScheduleListResult.Success s) {
            ObjectNode data = buildSuccessData(s);
            out.println(CommandOutput.formatResult(true, data, null, null,
                traceId, elapsedMs, format));
            return 0;
        } else if (result instanceof ScheduleListResult.Failure f) {
            out.println(CommandOutput.formatResult(false, null,
                f.code().name(), f.message(), traceId, elapsedMs, format));
            return CommandOutput.exitCodeFor(f.code());
        }
        out.println(CommandOutput.formatResult(false, null, "UNKNOWN",
            "unexpected result type", traceId, elapsedMs, format));
        return 1;
    }

    static ObjectNode buildSuccessData(ScheduleListResult.Success s) {
        ObjectNode data = JSON.createObjectNode();
        data.put("snapshotAt",
            s.snapshotAt().atZone(ZoneId.systemDefault()).format(SNAPSHOT_FMT));
        data.put("count", s.courses().size());

        ArrayNode courses = JSON.createArrayNode();
        for (CourseEntry c : s.courses()) {
            courses.add(buildCourseNode(c));
        }
        data.set("courses", courses);
        return data;
    }

    static ObjectNode buildCourseNode(CourseEntry c) {
        ObjectNode node = JSON.createObjectNode();
        node.put("courseName", c.courseName());
        node.put("section", c.section());
        node.put("teacher", c.teacher());
        node.put("room", c.room());
        node.put("weekday", c.weekday().code());
        node.put("weekdayName", c.weekday().displayName());
        node.put("beginUnit", c.period().beginUnit());
        node.put("endUnit", c.period().endUnit());
        node.put("startTime", c.period().startTime().toString());
        node.put("endTime", c.period().endTime().toString());
        node.put("weekRange", c.weekRange().compact());
        ArrayNode weeks = JSON.createArrayNode();
        for (int w : c.weekRange().weeks()) {
            weeks.add(w);
        }
        node.set("weeks", weeks);
        node.put("isAdjusted", c.isAdjusted());
        return node;
    }

    private static ObjectNode buildDryRunData() {
        ObjectNode data = JSON.createObjectNode();
        data.put("snapshotAt",
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).format(SNAPSHOT_FMT));
        data.put("count", 1);
        ArrayNode courses = JSON.createArrayNode();
        ObjectNode stub = JSON.createObjectNode();
        stub.put("courseName", "dry-run-course");
        stub.put("section", "00");
        stub.put("teacher", "dry-run-teacher");
        stub.put("room", "dry-run-room");
        stub.put("weekday", 1);
        stub.put("weekdayName", "星期一");
        stub.put("beginUnit", 1);
        stub.put("endUnit", 2);
        stub.put("startTime", "08:00");
        stub.put("endTime", "09:50");
        stub.put("weekRange", "1-17");
        stub.set("weeks", JSON.createArrayNode());
        stub.put("isAdjusted", false);
        courses.add(stub);
        data.set("courses", courses);
        return data;
    }
}
