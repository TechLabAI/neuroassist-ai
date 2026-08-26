package com.neuroassist.mig.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

@AnalyzeClasses(
        packages = "com.neuroassist.mig",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    // RULE-01: domain.* must not import org.springframework
    @ArchTest
    static final ArchRule rule01_domain_no_spring =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("org.springframework..");

    // RULE-02: domain.* must not import jakarta.persistence
    @ArchTest
    static final ArchRule rule02_domain_no_jpa =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("jakarta.persistence..");

    // RULE-04: api.* must not depend on infrastructure.*
    //
    // Anchored to this service's own packages, not "..api..". The loose form also matches
    // third-party packages that happen to be named `api` — `org.flywaydb.core.api` is the one
    // that surfaced it (E2E-016): the moment an infrastructure adapter touched Flyway's public
    // API, RULE-05 reported 16 violations that had nothing to do with hexagonal boundaries.
    // A guard that fires on the wrong thing gets muted, and a muted guard protects nothing.
    private static final String OWN_API = "com.neuroassist.mig.api..";
    private static final String OWN_INFRA = "com.neuroassist.mig.infrastructure..";

    @ArchTest
    static final ArchRule rule04_api_not_depends_infra =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideInAPackage(OWN_API)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(OWN_INFRA);

    // RULE-05: infrastructure.* must not depend on api.*
    @ArchTest
    static final ArchRule rule05_infra_not_depends_api =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideInAPackage(OWN_INFRA)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(OWN_API);

    // RULE-06: @FeignClient only in infrastructure.clients
    @ArchTest
    static final ArchRule rule06_feign_only_in_clients =
            ArchRuleDefinition.classes()
                    .that()
                    .areAnnotatedWith("org.springframework.cloud.openfeign.FeignClient")
                    .should()
                    .resideInAnyPackage("..infrastructure.clients..");

    // RULE-03: @Transactional only in application.service (CLAUDE.md)
    @ArchTest
    static final ArchRule rule03_transactional_only_in_application_service =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideOutsideOfPackage("..application.service..")
                    .should()
                    .beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    // RULE-07: TenantContextHolder.require() mandatory in tenant-scoped application.service
    // (CLAUDE.md)
    //   Not enforceable purely via ArchUnit — verified by:
    //     (a) code review of every @Service in application.service,
    //     (b) integration tests asserting MDC/TenantContextHolder set during processing.
    //   See SPEC-06 §"Tenant isolation" and CLIO-014.

    // RULE-08: spring.jpa.open-in-view=false mandatory in application.yml (CLAUDE.md)
    //   Not enforceable via ArchUnit — verified by:
    //     PropertyValidationIT in src/test/java reading the resolved Environment.
    //   The generator emits open-in-view: false in application.yml by default.

    // EXTRA: WebClient only in infrastructure.* (defensive — not part of CLAUDE.md RULE-08)
    @ArchTest
    static final ArchRule rule_extra_webclient_only_in_infrastructure =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideOutsideOfPackage("..infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(
                            "org.springframework.web.reactive.function.client.WebClient");
}
