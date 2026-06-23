package edu.szu.agent.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.szu.agent.domain.calendar.AcademicEvent;
import edu.szu.agent.domain.calendar.AcademicEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsonMappers}.
 *
 * <p>Guards the core contract that {@code java.time} values serialize as
 * ISO-8601 strings, not numeric arrays — the bug that made MCP tool results
 * emit dates like {@code [2026,3,4]}.
 *
 * @since 0.3.0
 * @author 王子豪
 */
@DisplayName("JsonMappers")
class JsonMappersTest {

    private final ObjectMapper mapper = JsonMappers.standard();

    @Test
    @DisplayName("LocalDate serializes as ISO string, not array")
    void localDateSerializesAsIsoString() throws Exception {
        String json = mapper.writeValueAsString(LocalDate.of(2026, 3, 4));

        assertThat(json).isEqualTo("\"2026-03-04\"");
        assertThat(json).doesNotContain("[");
    }

    @Test
    @DisplayName("LocalTime serializes as ISO string, not array")
    void localTimeSerializesAsIsoString() throws Exception {
        String json = mapper.writeValueAsString(LocalTime.of(19, 0));

        assertThat(json).isEqualTo("\"19:00:00\"");
    }

    @Test
    @DisplayName("Instant serializes as ISO string, not numeric seconds")
    void instantSerializesAsIsoString() throws Exception {
        String json = mapper.writeValueAsString(Instant.parse("2026-03-04T10:15:30Z"));

        assertThat(json).isEqualTo("\"2026-03-04T10:15:30Z\"");
    }

    @Test
    @DisplayName("domain record with LocalDate field serializes the date as ISO string")
    void domainRecordSerializesDateAsIsoString() throws Exception {
        AcademicEvent event = AcademicEvent.of(
            LocalDate.of(2026, 7, 18),
            AcademicEventType.BREAK,
            "暑假开始",
            "2025-2026-SPRING");

        String json = mapper.writeValueAsString(event);

        assertThat(json).contains("\"date\":\"2026-07-18\"");
        assertThat(json).doesNotContain("[2026");
    }

    @Test
    @DisplayName("ISO output round-trips back to the same value")
    void isoOutputRoundTrips() throws Exception {
        LocalDate original = LocalDate.of(2026, 3, 4);

        String json = mapper.writeValueAsString(original);
        LocalDate restored = mapper.readValue(json, LocalDate.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("legacy numeric-array form still deserializes (backward compatible)")
    void legacyArrayFormStillDeserializes() throws Exception {
        // A cache file written before this fix stored the date as an array.
        LocalDate restored = mapper.readValue("[2026,3,4]", LocalDate.class);

        assertThat(restored).isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    @DisplayName("each call returns an independent instance (no shared mutation)")
    void eachCallReturnsIndependentInstance() {
        ObjectMapper a = JsonMappers.standard();
        ObjectMapper b = JsonMappers.standard();

        a.enable(SerializationFeature.INDENT_OUTPUT);

        assertThat(a).isNotSameAs(b);
        assertThat(b.isEnabled(SerializationFeature.INDENT_OUTPUT)).isFalse();
    }
}
