package com.neuroassist.mig.infrastructure.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hands migration control to {@link MigrationJobRunner} when a {@code --mode} is given (E2E-030).
 *
 * <p>Without this, a {@code migrate-all} run would migrate twice: Spring Boot's Flyway
 * auto-configuration would first migrate whatever {@code FLYWAY_SCHEMAS} names at startup, and the
 * runner would then fan out over every schema. The first pass is not merely redundant — it is the
 * single-schema behaviour this issue exists to replace, and leaving it in would mean two different
 * answers to "what did this deploy migrate?" in one run.
 *
 * <p>A no-op {@link FlywayMigrationStrategy} rather than {@code spring.flyway.enabled: false}: the
 * auto-configured {@code Flyway} bean stays available, and the choice is expressed once, here,
 * instead of as a second environment variable that has to be kept in agreement with the mode.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "neuroassist.mig.mode")
public class JobModeFlywayConfig {

    @Bean
    public FlywayMigrationStrategy migrationHandledByJobRunner() {
        return flyway ->
                log.info(
                        "Startup migration skipped — a --mode was given, so MigrationJobRunner"
                                + " decides what gets migrated");
    }
}
