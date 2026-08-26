package com.neuroassist.mig.infrastructure.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.mig.application.port.in.MigrateAllUseCase;
import com.neuroassist.mig.application.port.in.MigrateAllUseCase.RunReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Deploy-time job face: one {@code --mode} per invocation (E2E-030, api-contract §2/§3).
 *
 * <p>Active only when {@code neuroassist.mig.mode} is set, so a run without it keeps the previous
 * behaviour exactly — Spring Boot's Flyway auto-configuration migrating whatever {@code
 * FLYWAY_SCHEMAS} names. That backwards compatibility is not politeness: {@code migrations-global}
 * still runs that way, and a change that silently altered what a deploy job migrates is the last
 * thing this service should do.
 *
 * <p><b>Exit codes are the pipeline's gate</b> (api-contract §3), and the three failure codes mean
 * different things on purpose:
 *
 * <ul>
 *   <li>{@code 1} — invocation error. Nothing ran; fix the command.
 *   <li>{@code 2} — one or more schemas failed. <b>The fleet is at mixed versions</b>: some
 *       companies are on the new schema and some are not, so the business services must NOT be
 *       rolled out.
 *   <li>{@code 3} — pre-flight failure. Nothing was attempted, so nothing is half-applied.
 * </ul>
 *
 * <p>Collapsing 2 and 3 into "it failed" would lose the only distinction an operator acts on: after
 * a 3 you retry, after a 2 you look at which schemas are named in the report.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "neuroassist.mig.mode")
public class MigrationJobRunner implements ApplicationRunner {

    static final int EXIT_OK = 0;
    static final int EXIT_INVOCATION_ERROR = 1;
    static final int EXIT_MIGRATION_FAILED = 2;
    static final int EXIT_PREFLIGHT_FAILED = 3;

    private static final String MODE_MIGRATE_ALL = "migrate-all";
    private static final String MODE_MIGRATE_GLOBAL = "migrate-global";

    private final MigrateAllUseCase migrateAllUseCase;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final String mode;
    private final boolean dryRun;

    public MigrationJobRunner(
            MigrateAllUseCase migrateAllUseCase,
            ApplicationContext applicationContext,
            ObjectMapper objectMapper,
            @Value("${neuroassist.mig.mode}") String mode,
            @Value("${neuroassist.mig.dry-run:false}") boolean dryRun) {
        this.migrateAllUseCase = migrateAllUseCase;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.mode = mode;
        this.dryRun = dryRun;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = execute();
        // The report is on stdout and the process ends with the code. A job that migrated nothing
        // and exited 0 is how "Schema is up to date" became a misleading success in this project.
        SpringApplication.exit(applicationContext, (ExitCodeGenerator) () -> exitCode);
        if (exitCode != EXIT_OK) {
            // exit() alone leaves a non-web context to finish normally with code 0.
            System.exit(exitCode);
        }
    }

    private int execute() {
        if (dryRun) {
            log.warn("--dry-run is not implemented yet; refusing rather than migrating silently");
            return EXIT_INVOCATION_ERROR;
        }

        RunReport report;
        switch (mode) {
            case MODE_MIGRATE_ALL -> report = migrateAllUseCase.migrateAll();
            case MODE_MIGRATE_GLOBAL -> report = migrateAllUseCase.migrateGlobal();
            default -> {
                log.error(
                        "Unknown --mode '{}'. Valid modes: {}, {}",
                        mode,
                        MODE_MIGRATE_ALL,
                        MODE_MIGRATE_GLOBAL);
                return EXIT_INVOCATION_ERROR;
            }
        }

        printReport(report);

        if (report.preflightFailed()) {
            return EXIT_PREFLIGHT_FAILED;
        }
        return report.anyFailed() ? EXIT_MIGRATION_FAILED : EXIT_OK;
    }

    private void printReport(RunReport report) {
        try {
            System.out.println(objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            // The log above already carries the same information per schema; a report that cannot
            // be serialised must not turn a successful migration run into a failed one.
            log.warn("Could not serialise the run report: {}", e.getMessage());
        }
    }
}
