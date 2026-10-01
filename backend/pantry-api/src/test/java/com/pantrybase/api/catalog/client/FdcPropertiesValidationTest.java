package com.pantrybase.api.catalog.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the startup contract of the FDC configuration: a misconfigured client has
 * to fail while the context loads, not later as an obscure provider error.
 */
class FdcPropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(FdcProperties.class)
    static class TestConfig {
    }

    @Test
    void validConfiguration_binds() {
        runner.withPropertyValues(
                        "app.catalog.fdc.api-key=REAL_KEY",
                        "app.catalog.fdc.base-url=https://api.nal.usda.gov/fdc/v1",
                        "app.catalog.fdc.data-types=Foundation,SR Legacy",
                        "app.catalog.fdc.page-size=20",
                        "app.catalog.fdc.connect-timeout=2s",
                        "app.catalog.fdc.read-timeout=5s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FdcProperties props = context.getBean(FdcProperties.class);
                    assertThat(props.apiKey()).isEqualTo("REAL_KEY");
                    assertThat(props.dataTypes()).containsExactly("Foundation", "SR Legacy");
                    assertThat(props.pageSize()).isEqualTo(20);
                    assertThat(props.isDemoKey()).isFalse();
                });
    }

    @Test
    void blankApiKey_failsAtStartup() {
        runner.withPropertyValues(
                        "app.catalog.fdc.api-key=",
                        "app.catalog.fdc.base-url=https://api.nal.usda.gov/fdc/v1",
                        "app.catalog.fdc.data-types=Foundation",
                        "app.catalog.fdc.page-size=20",
                        "app.catalog.fdc.connect-timeout=2s",
                        "app.catalog.fdc.read-timeout=5s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("api-key must not be blank");
                });
    }

    @Test
    void pageSizeBelowOne_failsAtStartup() {
        runner.withPropertyValues(
                        "app.catalog.fdc.api-key=REAL_KEY",
                        "app.catalog.fdc.base-url=https://api.nal.usda.gov/fdc/v1",
                        "app.catalog.fdc.data-types=Foundation",
                        "app.catalog.fdc.page-size=0",
                        "app.catalog.fdc.connect-timeout=2s",
                        "app.catalog.fdc.read-timeout=5s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("page-size must be at least 1");
                });
    }

    @Test
    void zeroTimeouts_failAtStartup() {
        runner.withPropertyValues(
                        "app.catalog.fdc.api-key=REAL_KEY",
                        "app.catalog.fdc.base-url=https://api.nal.usda.gov/fdc/v1",
                        "app.catalog.fdc.data-types=Foundation",
                        "app.catalog.fdc.page-size=20",
                        "app.catalog.fdc.connect-timeout=0s",
                        "app.catalog.fdc.read-timeout=0s")
                .run(context -> {
                    // Boot binds "0s" without complaint, so without this check a zero
                    // timeout would only appear as a mysteriously failing provider.
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("connect-timeout must be greater than zero")
                            .hasStackTraceContaining("read-timeout must be greater than zero");
                });
    }

    @Test
    void emptyDataTypes_failsAtStartup() {
        runner.withPropertyValues(
                        "app.catalog.fdc.api-key=REAL_KEY",
                        "app.catalog.fdc.base-url=https://api.nal.usda.gov/fdc/v1",
                        "app.catalog.fdc.data-types=",
                        "app.catalog.fdc.page-size=20",
                        "app.catalog.fdc.connect-timeout=2s",
                        "app.catalog.fdc.read-timeout=5s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("data-types must not be empty");
                });
    }

    @Test
    void demoKey_isRecognizedSoTheClientCanWarnAboutIt() {
        FdcProperties demo = new FdcProperties("DEMO_KEY", "https://api.nal.usda.gov/fdc/v1",
                List.of("Foundation"), 20, Duration.ofSeconds(2), Duration.ofSeconds(5));
        FdcProperties real = new FdcProperties("REAL_KEY", "https://api.nal.usda.gov/fdc/v1",
                List.of("Foundation"), 20, Duration.ofSeconds(2), Duration.ofSeconds(5));

        assertThat(demo.isDemoKey()).isTrue();
        assertThat(real.isDemoKey()).isFalse();
    }
}
