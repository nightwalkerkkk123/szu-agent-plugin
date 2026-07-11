package edu.szu.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.szu.agent.account.Account;
import edu.szu.agent.account.AccountResolutionException;
import edu.szu.agent.account.AccountResolver;
import edu.szu.agent.client.http.CampusHttpClient;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.client.http.EhallSessionManager;
import edu.szu.agent.client.http.EhallSportVenueClient;
import edu.szu.agent.client.http.EhallSportVenueClient.CampusInfo;
import edu.szu.agent.client.http.EhallSportVenueClient.SportInfo;
import edu.szu.agent.client.http.EhallSportVenueClient.SportVenueData;
import edu.szu.agent.client.session.HttpSession;
import edu.szu.agent.client.session.SessionStore;
import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import edu.szu.agent.observability.Tracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code direct-sports} subcommand — discover all campuses and sports offered
 * by the ehall sport-venue module via direct HTTP.
 *
 * <p>Outputs the raw {@code XQDM}/{@code XMDM} codes so that other commands
 * (e.g. {@code direct-slots}, {@code direct-book}) can use them directly.
 *
 * <p>// Design Pattern: Adapter (CLI wrapper around direct HTTP discovery)
 * // 编程技术: 注解 / Builder / try-with-resources
 *
 * @since 0.6.0
 * @author 王子豪
 */
@Command(
    name = "direct-sports",
    description = "List all campuses and sports from ehall (direct HTTP)",
    mixinStandardHelpOptions = true
)
public class DirectSportsCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = {"-u", "--username"}, description = "Student ID", required = true)
    private String username;

    @Option(names = {"--session-home"}, description = "Directory under which .szu-agent/sessions is created",
        defaultValue = "${sys:user.home}")
    private String sessionHome;

    @Option(names = {"--trust-all"}, description = "Disable TLS certificate validation (dev/internal only)")
    private boolean trustAll;

    @Option(names = {"-e", "--env-file"}, description = "Path to .env file for credentials")
    private String envFile;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        long startMs = System.currentTimeMillis();
        String traceId = Tracer.getInstance().generateTraceId();

        SessionStore store = new SessionStore(Path.of(sessionHome), username);
        if (!store.exists()) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.SESSION_NOT_FOUND.name(),
                "No persisted state for " + username + "; run direct-login first",
                traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.SESSION_NOT_FOUND);
        }

        Account account = resolveAccount();
        if (account == null) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.INVALID_REQUEST.name(),
                "Could not resolve credential for " + username
                    + " (set SZU_PASSWORD_" + username + ")", traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(ErrorCode.INVALID_REQUEST);
        }

        try {
            EhallSessionManager sessionManager = new EhallSessionManager(
                account.studentId(), account.password(), trustAll);
            CookieJar jar = loadOrCreateJar(store);

            try (CampusHttpClient http = sessionManager.ensureSession(jar)) {
                persistSession(store, http.cookieJar());
                EhallSportVenueClient api = new EhallSportVenueClient(http);
                SportVenueData data = api.getSportVenueData();

                ObjectNode result = JSON.createObjectNode();
                result.put("traceId", traceId);
                result.put("username", username);

                ArrayNode campuses = result.putArray("campuses");
                for (CampusInfo c : data.campuses()) {
                    ObjectNode node = campuses.addObject();
                    node.put("wid", c.wid());
                    node.put("code", c.code());
                    node.put("name", c.name());
                    node.put("sort", c.sort());
                }

                ArrayNode sports = result.putArray("sports");
                for (SportInfo s : data.sports()) {
                    ObjectNode node = sports.addObject();
                    node.put("wid", s.wid());
                    node.put("sportCode", s.sportCode());
                    node.put("sportName", s.sportName());
                    node.put("campusCodes", s.campusCodes());
                    node.put("dcfs", s.dcfs());
                    node.put("icon", s.icon());
                    node.put("sort", s.sort());
                }

                result.put("packageVenueCount", data.packageVenues().size());
                result.put("dismissalVenueCount", data.dismissalVenues().size());
                result.put("durationMs", System.currentTimeMillis() - startMs);

                out.println(CommandOutput.formatResult(true, result, null, null,
                    traceId, result.get("durationMs").asLong(), "json"));
                return 0;
            }
        } catch (BookingException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                e.code().name(), e.getMessage(), traceId, elapsed, "json"));
            return CommandOutput.exitCodeFor(e.code());
        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            out.println(CommandOutput.formatResult(false, null,
                ErrorCode.UNKNOWN.name(), "Unexpected error: " + e.getMessage(),
                traceId, elapsed, "json"));
            return 1;
        }
    }

    private Account resolveAccount() {
        try {
            return (envFile != null)
                ? AccountResolver.resolve(username, System.getenv(), Path.of(envFile))
                : AccountResolver.resolve(username, System.getenv());
        } catch (AccountResolutionException e) {
            return null;
        }
    }

    private static CookieJar loadOrCreateJar(SessionStore store) {
        if (!store.exists()) {
            return new CookieJar();
        }
        try {
            HttpSession session = HttpSession.read(store);
            return new CookieJar(session.cookies());
        } catch (IOException e) {
            return new CookieJar();
        }
    }

    private static void persistSession(SessionStore store, CookieJar jar) {
        try {
            HttpSession.write(store, jar);
        } catch (IOException e) {
            // Best-effort persistence; discovery still succeeded.
        }
    }
}
