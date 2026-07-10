package edu.szu.agent.cli;

import edu.szu.agent.error.BookingException;
import edu.szu.agent.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CampusSportCodeResolver")
class CampusSportCodeResolverTest {

    @Test
    @DisplayName("raw codes take precedence over names")
    void rawCodesWin() {
        CampusSportCodeResolver.Resolution r = CampusSportCodeResolver.resolve(
            "YUEHAI", "2", "TENNIS", "009");
        assertThat(r.campusCode()).isEqualTo("2");
        assertThat(r.sportCode()).isEqualTo("009");
    }

    @Test
    @DisplayName("resolves Chinese campus names")
    void resolvesChineseCampus() {
        CampusSportCodeResolver.Resolution r = CampusSportCodeResolver.resolve(
            "粤海", null, "健身", null);
        assertThat(r.campusCode()).isEqualTo("1");
        assertThat(r.campusDisplayName()).isEqualTo("粤海校区");
    }

    @Test
    @DisplayName("resolves Chinese sport names")
    void resolvesChineseSport() {
        CampusSportCodeResolver.Resolution r = CampusSportCodeResolver.resolve(
            null, "1", "羽毛球", null);
        assertThat(r.sportCode()).isEqualTo("001");
        assertThat(r.sportDisplayName()).isEqualTo("羽毛球");
    }

    @Test
    @DisplayName("resolves enum names")
    void resolvesEnumNames() {
        CampusSportCodeResolver.Resolution r = CampusSportCodeResolver.resolve(
            "LIHU", null, "TENNIS", null);
        assertThat(r.campusCode()).isEqualTo("2");
        assertThat(r.sportCode()).isEqualTo("004");
    }

    @Test
    @DisplayName("rejects missing campus")
    void rejectsMissingCampus() {
        assertThatThrownBy(() -> CampusSportCodeResolver.resolve(
            null, null, "TENNIS", null))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("rejects missing sport")
    void rejectsMissingSport() {
        assertThatThrownBy(() -> CampusSportCodeResolver.resolve(
            "YUEHAI", null, null, null))
            .isInstanceOf(BookingException.class)
            .satisfies(e -> assertThat(((BookingException) e).code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
