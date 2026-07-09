package edu.szu.agent.client.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.agent.client.http.CookieJar;
import edu.szu.agent.json.JsonMappers;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Serializable snapshot of an HTTP login session.
 *
 * <p>Contains enough state (username, save timestamp, and cookies) for
 * {@link edu.szu.agent.client.http.CampusHttpClient} to resume a previous
 * CAS login without re-authenticating.
 *
 * <p>// Design Pattern: Memento (persistent capture of client state)
 * // 编程技术: record / Jackson / 不可变集合
 *
 * @since 0.6.0
 * @author 王子豪
 */
public record HttpSession(String username, Instant savedAt, List<CookieJar.Cookie> cookies) {

    private static final ObjectMapper MAPPER = JsonMappers.standard();

    /**
     * Canonical constructor — defensively copies the cookie list.
     *
     * @since 0.6.0
     * @author 王子豪
     */
    public HttpSession {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(savedAt, "savedAt");
        cookies = List.copyOf(cookies == null ? List.of() : cookies);
    }

    /**
     * Reads a persisted session from disk.
     *
     * @param store session storage abstraction
     * @return the deserialized session
     * @throws IOException if the file is missing or cannot be parsed
     * @since 0.6.0
     * @author 王子豪
     */
    public static HttpSession read(SessionStore store) throws IOException {
        Objects.requireNonNull(store, "store");
        String json = store.read();
        return MAPPER.readValue(json, HttpSession.class);
    }

    /**
     * Writes the current cookie jar to disk as a session snapshot.
     *
     * @param store session storage abstraction
     * @param jar   cookie jar to persist
     * @throws IOException if the write fails
     * @since 0.6.0
     * @author 王子豪
     */
    public static void write(SessionStore store, CookieJar jar) throws IOException {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(jar, "jar");
        HttpSession session = new HttpSession(
            store.username(), Instant.now(), jar.snapshot());
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(session);
        store.write(json);
    }
}
