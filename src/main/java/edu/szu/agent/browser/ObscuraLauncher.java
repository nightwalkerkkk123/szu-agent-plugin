package edu.szu.agent.browser;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Starts and supervises the bundled Obscura CDP daemon.
 *
 * <p>The launcher mirrors Playwright's "browser binary is an implementation
 * detail" ergonomics: callers ask for a {@link BrowserLifecycle}, and this
 * class ensures an Obscura process is available behind {@code connectOverCDP}.
 *
 * <p>// Design Pattern: Process Supervisor (GoF Singleton + Lifecycle seam)
 * <p>// 编程技术: NIO.2 / HttpClient / 进程管理 / Lambda(shutdown hook) / volatile
 *
 * @since 0.6.0
 * @author 王子豪
 */
public final class ObscuraLauncher {

    private static final Logger log = LoggerFactory.getLogger(ObscuraLauncher.class);
    private static final URI DEFAULT_VERSION_URI = URI.create("http://127.0.0.1:9222/json/version");
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(30);
    private static final long READY_POLL_MS = 500L;
    private static final Set<PosixFilePermission> EXECUTABLE_PERMS =
        PosixFilePermissions.fromString("rwx------");

    private static final Object LOCK = new Object();
    private static volatile Process managedProcess;

    private ObscuraLauncher() {
    }

    /**
     * Ensures an Obscura daemon is listening on the default CDP control port.
     *
     * @throws BookingException with BROWSER_CRASH for extraction/start failures,
     *         or NETWORK_TIMEOUT when the process never becomes ready
     * @since 0.6.0
     * @author 王子豪
     */
    public static void ensureRunning() {
        ensureRunning(Path.of(System.getProperty("user.home")), DEFAULT_VERSION_URI);
    }

    /**
     * Idempotently starts an Obscura daemon on the port implied by
     * {@code versionUri}. Equivalent to {@link #ensureRunning()} when
     * {@code versionUri} is the default {@code http://127.0.0.1:9222/json/version}.
     *
     * @param versionUri the version-probe endpoint for the daemon
     * @since 0.2.0
     * @author 王子豪
     */
    public static void ensureRunning(URI versionUri) {
        ensureRunning(Path.of(System.getProperty("user.home")), versionUri);
    }

    /**
     * Test seam for using a temporary home directory and endpoint.
     *
     * @param home       user home under which {@code .szu-agent/bin} lives
     * @param versionUri Obscura control-plane version endpoint
     * @since 0.6.0
     * @author 王子豪
     */
    static void ensureRunning(Path home, URI versionUri) {
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(versionUri, "versionUri");
        synchronized (LOCK) {
            if (isRunning(versionUri)) {
                return;
            }
            Path binary = binaryPath(home);
            extractBinary(home, binaryName());
            extractBinary(home, workerName());
            // Re-probe after extraction: another JVM may have bound 9222 in the
            // meantime. Without this, two concurrent callers both pass the first
            // probe, both try ProcessBuilder.start(), and the second fails with
            // an obscure BindException wrapped as BROWSER_CRASH.
            if (isRunning(versionUri)) {
                return;
            }
            startProcess(home, binary, portOf(versionUri));
            waitUntilReady(versionUri);
        }
    }

    /**
     * Returns whether the default daemon endpoint currently responds.
     *
     * @since 0.6.0
     * @author 王子豪
     */
    public static boolean isRunning() {
        return isRunning(DEFAULT_VERSION_URI);
    }

    /**
     * Returns the extracted Obscura binary path under the current user home.
     *
     * @since 0.6.0
     * @author 王子豪
     */
    public static Path binaryPath() {
        return binaryPath(Path.of(System.getProperty("user.home")));
    }

    /**
     * Returns the PID file path under the current user home.
     *
     * @since 0.6.0
     * @author 王子豪
     */
    public static Path pidFile() {
        return pidFile(Path.of(System.getProperty("user.home")));
    }

    static boolean isRunning(URI versionUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(versionUri)
                .timeout(PROBE_TIMEOUT)
                .GET()
                .build();
            HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    static Path binaryPath(Path home) {
        return binDir(home).resolve(binaryName());
    }

    static Path pidFile(Path home) {
        return home.resolve(".szu-agent/obscura.pid");
    }

    private static Path binDir(Path home) {
        return home.resolve(".szu-agent/bin");
    }

    private static Path logFile(Path home) {
        return home.resolve(".szu-agent/obscura.log");
    }

    private static String binaryName() {
        return isWindows() ? "obscura.exe" : "obscura";
    }

    /** Visible for testing — extract port from the version URI (defaults to 9222). */
    static int portOf(URI versionUri) {
        Objects.requireNonNull(versionUri, "versionUri");
        int port = versionUri.getPort();
        return port == -1 ? 9222 : port;
    }

    /**
     * Convert a Playwright-style {@code ws://host:port[/path]} endpoint into the
     * corresponding Obscura version-probe {@code http://host:port/json/version}.
     *
     * @param wsUrl the WebSocket CDP endpoint (must start with {@code ws://})
     * @return the matching version URI
     * @throws IllegalArgumentException if input is null, blank, or not {@code ws://}
     * @since 0.2.0
     * @author 王子豪
     */
    public static URI versionUriFromWsUrl(String wsUrl) {
        if (wsUrl == null || wsUrl.isBlank()) {
            throw new IllegalArgumentException("wsUrl must be non-blank");
        }
        if (!wsUrl.startsWith("ws://")) {
            throw new IllegalArgumentException("wsUrl must use ws:// scheme: " + wsUrl);
        }
        return URI.create(wsUrl.replaceFirst("^ws://", "http://") + "/json/version");
    }

    private static String workerName() {
        return isWindows() ? "obscura-worker.exe" : "obscura-worker";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void extractBinary(Path home, String fileName) {
        Path target = binDir(home).resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            // Size-only dedup would miss a same-sized replacement; verify the
            // first 4 bytes look like a real executable (PE/ELF/Mach-O) before
            // skipping the copy. Cheap; no hashing library required.
            if (Files.exists(target) && hasExecutableMagic(target)) {
                setExecutableIfSupported(target);
                return;
            }
            String resourceName = "/bin/" + fileName;
            try (InputStream in = ObscuraLauncher.class.getResourceAsStream(resourceName)) {
                if (in == null) {
                    throw new BookingException(ErrorCode.BROWSER_CRASH,
                        "bundled Obscura binary not found: " + resourceName);
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setExecutableIfSupported(target);
        } catch (BookingException e) {
            throw e;
        } catch (IOException e) {
            throw new BookingException(ErrorCode.BROWSER_CRASH,
                "failed to extract Obscura binary " + fileName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns true if the first 4 bytes of the file look like a real binary:
     * PE (MZ = "MZ") on Windows, ELF (ELF) on Linux,
     * or Mach-O (0xCFFAEDFE / 0xCEFAEDFE feedface) on macOS. Anything else
     * (e.g. an HTTP 404 page mistakenly cached, or a partial write) is
     * treated as missing and triggers a re-extraction.
     */
    static boolean hasExecutableMagic(Path target) throws IOException {
        if (Files.size(target) < 4) {
            return false;
        }
        try (InputStream in = Files.newInputStream(target)) {
            byte[] head = in.readNBytes(4);
            if (head.length < 4) {
                return false;
            }
            // PE: "MZ"
            if (head[0] == (byte) 0x4D && head[1] == (byte) 0x5A) {
                return true;
            }
            // ELF: 0x7F 'E' 'L' 'F'
            if (head[0] == (byte) 0x7F && head[1] == (byte) 'E'
                && head[2] == (byte) 'L' && head[3] == (byte) 'F') {
                return true;
            }
            // Mach-O: 0xCFFAEDFE (LE) or 0xCEFAEDFE (BE) — first 4 bytes
            if (head[0] == (byte) 0xCF && head[1] == (byte) 0xFA
                && head[2] == (byte) 0xED && head[3] == (byte) 0xFE) {
                return true;
            }
            if (head[0] == (byte) 0xCE && head[1] == (byte) 0xFA
                && head[2] == (byte) 0xED && head[3] == (byte) 0xFE) {
                return true;
            }
            return false;
        }
    }

    private static void setExecutableIfSupported(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, EXECUTABLE_PERMS);
        } catch (UnsupportedOperationException ignored) {
            // Windows / non-POSIX: use ACL inheritance and ProcessBuilder execution.
        }
    }

    private static void startProcess(Path home, Path binary, int port) {
        try {
            Files.createDirectories(home.resolve(".szu-agent"));
            ProcessBuilder builder = new ProcessBuilder(
                binary.toString(), "serve",
                "--host", "127.0.0.1",
                "--port", Integer.toString(port));
            builder.directory(binary.getParent().toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile(home).toFile()));
            builder.redirectError(ProcessBuilder.Redirect.appendTo(logFile(home).toFile()));
            Process process = builder.start();
            managedProcess = process;
            Files.writeString(pidFile(home), Long.toString(process.pid()));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stopManagedProcess(process), "obscura-shutdown"));
            log.info("Started Obscura daemon pid={} port={} binary={}", process.pid(), port, binary);
        } catch (IOException e) {
            throw new BookingException(ErrorCode.BROWSER_CRASH,
                "failed to start Obscura daemon: " + e.getMessage(), e);
        }
    }

    private static void waitUntilReady(URI versionUri) {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isRunning(versionUri)) {
                return;
            }
            try {
                Thread.sleep(READY_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
                    "interrupted while waiting for Obscura daemon", e);
            }
        }
        throw new BookingException(ErrorCode.NETWORK_TIMEOUT,
            "Obscura daemon did not become ready within " + READY_TIMEOUT.toSeconds() + "s");
    }

    private static void stopManagedProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
