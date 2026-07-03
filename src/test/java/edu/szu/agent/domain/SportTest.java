package edu.szu.agent.domain;

import edu.szu.agent.client.step.CapacityVenueSelector;
import edu.szu.agent.client.step.CourtListSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the sealed {@link Sport} hierarchy and its
 * campus-routed factory.
 *
 * @since 0.6.0
 * @author 王子豪
 */
@DisplayName("Sport sealed hierarchy")
class SportTest {

    @Test
    @DisplayName("YuehaiSport.TENNIS exposes displayName/ehallCode/campus")
    void yuehaiTennisMetadata() {
        Sport sport = YuehaiSport.TENNIS;

        assertThat(sport.displayName()).isEqualTo("网球");
        assertThat(sport.ehallCode()).isEqualTo("tennis");
        assertThat(sport.campus()).isEqualTo(Campus.YUEHAI);
    }

    @Test
    @DisplayName("LihuSport.TENNIS exposes the LIHU campus, even with same name")
    void lihuTennisRoutesToLihu() {
        Sport sport = LihuSport.TENNIS;

        assertThat(sport.displayName()).isEqualTo("网球");
        assertThat(sport.campus()).isEqualTo(Campus.LIHU);
    }

    @Test
    @DisplayName("Sport.of routes name through the supplied campus enum")
    void ofRoutesByCampus() {
        assertThat(Sport.of(Campus.YUEHAI, "TENNIS")).isSameAs(YuehaiSport.TENNIS);
        assertThat(Sport.of(Campus.LIHU, "TENNIS")).isSameAs(LihuSport.TENNIS);
        assertThat(Sport.of(Campus.LIHU, "PICKLEBALL")).isSameAs(LihuSport.PICKLEBALL);
    }

    @Test
    @DisplayName("Sport.of throws when sport not in the campus's enum")
    void ofRejectsCrossCampusSport() {
        // PICKLEBALL exists at LIHU but not YUEHAI.
        assertThatThrownBy(() -> Sport.of(Campus.YUEHAI, "PICKLEBALL"))
            .isInstanceOf(IllegalArgumentException.class);
        // GYM_HEAVY exists at YUEHAI but not LIHU.
        assertThatThrownBy(() -> Sport.of(Campus.LIHU, "GYM_HEAVY"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("All YuehaiSport constants have non-blank metadata + correct campus")
    void yuehaiConstantsArePopulated() {
        for (YuehaiSport sport : YuehaiSport.values()) {
            assertThat(sport.displayName()).as(sport.name()).isNotBlank();
            assertThat(sport.ehallCode()).as(sport.name()).isNotBlank();
            assertThat(sport.campus()).isEqualTo(Campus.YUEHAI);
            assertThat(sport.venueSelector()).as(sport.name()).isNotNull();
        }
    }

    @Test
    @DisplayName("All LihuSport constants have non-blank metadata + correct campus")
    void lihuConstantsArePopulated() {
        for (LihuSport sport : LihuSport.values()) {
            assertThat(sport.displayName()).as(sport.name()).isNotBlank();
            assertThat(sport.ehallCode()).as(sport.name()).isNotBlank();
            assertThat(sport.campus()).isEqualTo(Campus.LIHU);
            assertThat(sport.venueSelector()).as(sport.name()).isNotNull();
        }
    }

    @Test
    @DisplayName("Court sports bind CourtListSelector; gym sports bind CapacityVenueSelector")
    void venueSelectorBindings() {
        assertThat(YuehaiSport.TENNIS.venueSelector()).isInstanceOf(CourtListSelector.class);
        assertThat(YuehaiSport.GYM_AEROBIC.venueSelector())
            .isInstanceOf(CapacityVenueSelector.class);
        assertThat(YuehaiSport.GYM_HEAVY.venueSelector())
            .isInstanceOf(CapacityVenueSelector.class);
        assertThat(LihuSport.GYM.venueSelector()).isInstanceOf(CapacityVenueSelector.class);
        assertThat(LihuSport.TENNIS.venueSelector()).isInstanceOf(CourtListSelector.class);
    }

    @Test
    @DisplayName("YuehaiSport has 9 constants and LihuSport has 15")
    void countsMatchEhallPage() {
        assertThat(YuehaiSport.values()).hasSize(9);
        assertThat(LihuSport.values()).hasSize(15);
    }
}
