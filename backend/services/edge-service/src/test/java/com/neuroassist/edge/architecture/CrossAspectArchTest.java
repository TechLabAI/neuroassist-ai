package com.neuroassist.edge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cross-aspect ArchUnit tests — verify that the generated/edited code respects the architectural
 * decisions captured in {@code arch/decisions/*.arch.json}.
 *
 * <p>The catalogues are baked in as static {@code Set<String>} below. To pick up changes to a
 * decision, re-run {@code be-module-generator} for this service (or {@code be-architect-upgrade}
 * after the workspace bumps a version). The drift-check (D-055) will tell you when this is needed.
 *
 * <p>D-067 (Tier 2.1 — cross-aspect validation).
 */
@AnalyzeClasses(
        packages = "com.neuroassist.edge",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class CrossAspectArchTest {

    // ── Baked catalogues ────────────────────────────────────────────────

    private static final Set<String> PERMISSION_CATALOGUE =
            Set.of(
                    "edge.manage",
                    "edge.device",
                    "edge.read",
                    "edge.internal",
                    "edge.device-registration.aposentar",
                    "edge.device-registration.criar",
                    "edge.heartbeat.criar",
                    "edge.liveness.read",
                    "edge.liveness.resolver",
                    "edge.pairing.criar",
                    "edge.pairing.excluir",
                    "edge.profile-pointer.read",
                    "evnt.event-confirmation.confirmar",
                    "evnt.event-query.read",
                    "evnt.notification-orchestration.processar",
                    "evnt.notification-orchestration.reprocessar",
                    "evnt.report-ingress.ingerir",
                    "gest.detection-contract.read",
                    "gest.detection-profile.publicar",
                    "gest.detection-profile.read",
                    "gest.eligibility.read",
                    "gest.tipo-gesto.criar",
                    "gest.tipo-gesto.editar",
                    "gest.tipo-gesto.read",
                    "pac.catalogo-ativo.read",
                    "pac.comando-authoring.criar",
                    "pac.comando-authoring.editar",
                    "pac.comando-lifecycle.aposentar",
                    "pac.comando-lifecycle.publicar",
                    "pac.comando-lifecycle.read",
                    "pac.comando-lifecycle.recalibrar",
                    "pac.paciente.criar",
                    "pac.paciente.editar",
                    "pac.paciente.read");

    private static final Set<String> ENTITY_SCOPE_FILTER_NAMES =
            Set.of(
                    // (no entity-scope.arch.json pinned at generation time)
                    );

    private static final Set<String> EVENT_DESTINATIONS =
            Set.of(
                    // (no events.arch.json pinned at generation time)
                    );

    private static final Pattern HAS_AUTHORITY =
            Pattern.compile("hasAuthority\\(\\s*'([^']+)'\\s*\\)");

    // ── CRA-01: @PreAuthorize codes must exist in permission-catalogue ─

    @ArchTest
    static final ArchRule cra01_preauthorize_in_catalogue =
            methods()
                    .that()
                    .areAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize")
                    .should(
                            new ArchCondition<JavaMethod>(
                                    "use hasAuthority(...) referencing a code declared in permission-catalogue.arch.json") {
                                @Override
                                public void check(JavaMethod method, ConditionEvents events) {
                                    if (PERMISSION_CATALOGUE.isEmpty()) {
                                        return; // no catalogue pinned — skip silently
                                    }
                                    method.getAnnotations().stream()
                                            .filter(
                                                    a ->
                                                            a.getRawType()
                                                                    .getName()
                                                                    .equals(
                                                                            "org.springframework.security.access.prepost.PreAuthorize"))
                                            .findFirst()
                                            .flatMap(a -> a.get("value"))
                                            .map(Object::toString)
                                            .ifPresent(
                                                    value -> {
                                                        Matcher m = HAS_AUTHORITY.matcher(value);
                                                        while (m.find()) {
                                                            String code = m.group(1);
                                                            if (!PERMISSION_CATALOGUE.contains(
                                                                    code)) {
                                                                events.add(
                                                                        SimpleConditionEvent
                                                                                .violated(
                                                                                        method,
                                                                                        method
                                                                                                        .getFullName()
                                                                                                + " uses permission '"
                                                                                                + code
                                                                                                + "' which is NOT declared in permission-catalogue.arch.json"));
                                                            }
                                                        }
                                                    });
                                }
                            });

    // ── CRA-02: @org.hibernate.annotations.Filter names ────────────────

    @ArchTest
    static final ArchRule cra02_jpa_filter_names_in_entity_scope =
            classes()
                    .that()
                    .areAnnotatedWith("org.hibernate.annotations.Filter")
                    .or()
                    .areAnnotatedWith("org.hibernate.annotations.Filters")
                    .should(
                            new ArchCondition<JavaClass>(
                                    "use @Filter(name = ...) referencing a filter_name declared in entity-scope.arch.json") {
                                @Override
                                public void check(JavaClass clazz, ConditionEvents events) {
                                    if (ENTITY_SCOPE_FILTER_NAMES.isEmpty()) {
                                        return;
                                    }
                                    clazz.getAnnotations().stream()
                                            .filter(
                                                    a ->
                                                            a.getRawType()
                                                                    .getName()
                                                                    .equals(
                                                                            "org.hibernate.annotations.Filter"))
                                            .forEach(a -> checkFilterName(clazz, a, events));
                                    clazz.getAnnotations().stream()
                                            .filter(
                                                    a ->
                                                            a.getRawType()
                                                                    .getName()
                                                                    .equals(
                                                                            "org.hibernate.annotations.Filters"))
                                            .forEach(
                                                    a ->
                                                            a.get("value")
                                                                    .ifPresent(
                                                                            v -> {
                                                                                if (v
                                                                                        instanceof
                                                                                        Object[]
                                                                                                        arr) {
                                                                                    for (Object
                                                                                            item :
                                                                                                    arr) {
                                                                                        if (item
                                                                                                instanceof
                                                                                                com
                                                                                                                                .tngtech
                                                                                                                                .archunit
                                                                                                                                .core
                                                                                                                                .domain
                                                                                                                                .JavaAnnotation<
                                                                                                                        ?>
                                                                                                                inner) {
                                                                                            checkFilterName(
                                                                                                    clazz,
                                                                                                    inner,
                                                                                                    events);
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }));
                                }

                                private void checkFilterName(
                                        JavaClass clazz,
                                        com.tngtech.archunit.core.domain.JavaAnnotation<?> ann,
                                        ConditionEvents events) {
                                    ann.get("name")
                                            .map(Object::toString)
                                            .ifPresent(
                                                    name -> {
                                                        if (!ENTITY_SCOPE_FILTER_NAMES.contains(
                                                                name)) {
                                                            events.add(
                                                                    SimpleConditionEvent.violated(
                                                                            clazz,
                                                                            clazz.getName()
                                                                                    + " declares @Filter(name=\""
                                                                                    + name
                                                                                    + "\") which is NOT in entity-scope.arch.json"));
                                                        }
                                                    });
                                }
                            });

    // ── CRA-03: @FilterDef names must match entity-scope ───────────────

    @ArchTest
    static final ArchRule cra03_filter_def_names_in_entity_scope =
            classes()
                    .that()
                    .areAnnotatedWith("org.hibernate.annotations.FilterDef")
                    .or()
                    .areAnnotatedWith("org.hibernate.annotations.FilterDefs")
                    .should(
                            new ArchCondition<JavaClass>(
                                    "declare @FilterDef(name = ...) for filter_name declared in entity-scope.arch.json") {
                                @Override
                                public void check(JavaClass clazz, ConditionEvents events) {
                                    if (ENTITY_SCOPE_FILTER_NAMES.isEmpty()) {
                                        return;
                                    }
                                    clazz.getAnnotations().stream()
                                            .filter(
                                                    a ->
                                                            a.getRawType()
                                                                    .getName()
                                                                    .equals(
                                                                            "org.hibernate.annotations.FilterDef"))
                                            .forEach(a -> checkDefName(clazz, a, events));
                                    clazz.getAnnotations().stream()
                                            .filter(
                                                    a ->
                                                            a.getRawType()
                                                                    .getName()
                                                                    .equals(
                                                                            "org.hibernate.annotations.FilterDefs"))
                                            .forEach(
                                                    a ->
                                                            a.get("value")
                                                                    .ifPresent(
                                                                            v -> {
                                                                                if (v
                                                                                        instanceof
                                                                                        Object[]
                                                                                                        arr) {
                                                                                    for (Object
                                                                                            item :
                                                                                                    arr) {
                                                                                        if (item
                                                                                                instanceof
                                                                                                com
                                                                                                                                .tngtech
                                                                                                                                .archunit
                                                                                                                                .core
                                                                                                                                .domain
                                                                                                                                .JavaAnnotation<
                                                                                                                        ?>
                                                                                                                inner) {
                                                                                            checkDefName(
                                                                                                    clazz,
                                                                                                    inner,
                                                                                                    events);
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }));
                                }

                                private void checkDefName(
                                        JavaClass clazz,
                                        com.tngtech.archunit.core.domain.JavaAnnotation<?> ann,
                                        ConditionEvents events) {
                                    ann.get("name")
                                            .map(Object::toString)
                                            .ifPresent(
                                                    name -> {
                                                        if (!ENTITY_SCOPE_FILTER_NAMES.contains(
                                                                name)) {
                                                            events.add(
                                                                    SimpleConditionEvent.violated(
                                                                            clazz,
                                                                            clazz.getName()
                                                                                    + " declares @FilterDef(name=\""
                                                                                    + name
                                                                                    + "\") not declared in entity-scope.arch.json"));
                                                        }
                                                    });
                                }
                            });

    // ── CRA-04: Producer DESTINATION fields must be String constants ───
    //
    // ArchUnit doesn't reliably expose static-field initializer values
    // across versions, so we validate the SHAPE (`private static final
    // String DESTINATION`) and leave the value match to a unit test
    // emitted alongside each Producer. Producer shape: in package
    // `infrastructure.messaging.producer`, class name ends with `Producer`,
    // has a static final String DESTINATION.

    @ArchTest
    static final ArchRule cra04_producer_destination_shape =
            fields().that()
                    .haveName("DESTINATION")
                    .and()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("..infrastructure.messaging.producer..")
                    .should(
                            new ArchCondition<JavaField>(
                                    "be a `private static final String` constant — value must match an entry in events.arch.json (verified by a sibling unit test)") {
                                @Override
                                public void check(JavaField field, ConditionEvents events) {
                                    if (!field.getRawType().getName().equals("java.lang.String")) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        field,
                                                        field.getFullName()
                                                                + " DESTINATION must be a String"));
                                        return;
                                    }
                                    if (!field.getModifiers()
                                                    .contains(
                                                            com.tngtech.archunit.core.domain
                                                                    .JavaModifier.STATIC)
                                            || !field.getModifiers()
                                                    .contains(
                                                            com.tngtech.archunit.core.domain
                                                                    .JavaModifier.FINAL)) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        field,
                                                        field.getFullName()
                                                                + " DESTINATION must be static final"));
                                    }
                                }
                            });

    // ── CRA-05: Static guard for the catalogues themselves ─────────────
    // Documents (in test output) the catalogue sizes; not an enforcement
    // rule but a quick diagnostic during code review.

    @ArchTest
    static final ArchRule cra05_documented_catalogues =
            classes()
                    .that()
                    .haveSimpleName("CrossAspectArchTest")
                    .should()
                    .bePublic()
                    .as(
                            "baked catalogues: "
                                    + PERMISSION_CATALOGUE.size()
                                    + " permissions, "
                                    + ENTITY_SCOPE_FILTER_NAMES.size()
                                    + " filter names, "
                                    + EVENT_DESTINATIONS.size()
                                    + " event destinations");
}
