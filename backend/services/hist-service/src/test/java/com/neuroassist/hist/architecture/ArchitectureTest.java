package com.neuroassist.hist.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

@AnalyzeClasses(
        packages = "com.neuroassist.hist",
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
    @ArchTest
    static final ArchRule rule04_api_not_depends_infra =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");

    // RULE-05: infrastructure.* must not depend on api.*
    @ArchTest
    static final ArchRule rule05_infra_not_depends_api =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideInAPackage("..infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..api..");

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

    // RULE-E2E-024: no adapter in infrastructure.persistence.adapter may throw
    // UnsupportedOperationException.  An adapter whose only body is "throw new
    // UnsupportedOperationException(...)" lies about runtime capability and makes every
    // module gate green while the feature is actually missing (E2E-024).
    //
    // Detection: at bytecode level, "throw new UnsupportedOperationException(...)" compiles
    // to a constructor call on that class.  Checking that no adapter class has a bytecode
    // dependency on UnsupportedOperationException catches all such stubs.
    @ArchTest
    static final ArchRule ruleNoUnimplementedAdapters =
            ArchRuleDefinition.noClasses()
                    .that()
                    .resideInAPackage("..infrastructure.persistence.adapter..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.lang.UnsupportedOperationException")
                    .because(
                            "adapters must be fully implemented — a stub that throws"
                                    + " UnsupportedOperationException lies about capability"
                                    + " (E2E-024). Implement the adapter or remove it.");
}
