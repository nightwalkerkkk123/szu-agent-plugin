package edu.szu.agent;

import edu.szu.agent.cli.Main;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test verifying skeleton setup: Main class loads, picocli works, version defined.
 *
 * @since 0.1.0
 * @author 王子豪
 */
class SkeletonSmokeTest {

    @Test
    void mainClassCanBeInstantiated() {
        Main main = new Main();
        assertThat(main.call()).isEqualTo(0);
    }

    @Test
    void versionIsDefined() {
        assertThat(Main.class.getAnnotation(picocli.CommandLine.Command.class))
            .isNotNull();
        assertThat(Main.class.getAnnotation(picocli.CommandLine.Command.class).version())
            .containsExactly("0.1.0");
    }
}
