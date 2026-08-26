package com.neuroassist.evnt.bdd;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.CommandLookupPort;
import com.neuroassist.evnt.application.port.out.GestValidationPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.infrastructure.persistence.repository.EventoJpaRepository;
import com.neuroassist.evnt.infrastructure.persistence.repository.NotificationDispatchJpaRepository;
import com.neuroassist.evnt.infrastructure.persistence.repository.ReportIdempotencyJpaRepository;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;

/**
 * Cucumber lifecycle hooks: reset mocks before each scenario and clean database after. Singleton
 * Spring bean — injected repositories and mocked ports are reused across scenarios.
 *
 * <p>E2E-022 note: {@link TenantContextHolder} must be bound on the test thread before any direct
 * repository call in a {@code @Given} or {@code @After} step. Without it, {@code
 * TenantIdentifierResolver} falls back to {@code neuroassist_global}, and {@code
 * MultiTenantConnectionProvider} routes the connection there — where the evnt tables do not exist.
 * The fix: bind the default tenant in {@code @Before} and clear it in {@code @After}. HTTP requests
 * are unaffected: the server thread gets its own binding from {@code TenantContextFilter}.
 */
@RequiredArgsConstructor
public class CucumberHooks {

    private final GestValidationPort gestValidationPort;

    private final CommandLookupPort commandLookupPort;

    private final CaregiverRoutingPort caregiverRoutingPort;

    private final NotificationDispatchPort notificationDispatchPort;

    private final AuditTrailPort auditTrailPort;

    private final EventoJpaRepository eventoJpaRepository;

    private final NotificationDispatchJpaRepository notificationDispatchJpaRepository;

    private final ReportIdempotencyJpaRepository reportIdempotencyJpaRepository;

    /**
     * Before each scenario: bind the default tenant on the test thread, then reset all mocks.
     *
     * <p>Binding must happen before any {@code @Given} step calls a JPA repository directly. The
     * default is "empresa_demo" — all current BDD scenarios use this tenant. If a future scenario
     * needs a different tenant, the {@code @Given} step that seeds data for that tenant must call
     * {@code TenantContextHolder.set(...)} immediately before the repository call and restore the
     * previous value after.
     */
    @Before
    public void beforeScenario() {
        // E2E-022: bind tenant so direct repository calls in @Given steps route to the right
        // schema.
        TenantContextHolder.set("empresa_demo");

        reset(
                gestValidationPort,
                commandLookupPort,
                caregiverRoutingPort,
                notificationDispatchPort,
                auditTrailPort);

        // Default: GEST accepts all reports
        when(gestValidationPort.validate(any())).thenReturn(true);

        // Default: command "cmd-emergency" is urgencia=emergencia
        when(commandLookupPort.lookup(anyString(), anyString()))
                .thenReturn(
                        new CommandLookupPort.CommandSnapshot(
                                "cmd-emergency", "Emergency Command", "emergencia"));

        // Default: caregiver BddIds.CUIDADOR is responsible for every patient, and is known to ACS
        when(caregiverRoutingPort.responsibleFor(anyString()))
                .thenReturn(
                        java.util.List.of(
                                new CaregiverRoutingPort.ResponsibleCaregiver(
                                        BddIds.CUIDADOR, "CUIDADORA")));
        when(caregiverRoutingPort.isKnownCaregiver(anyString())).thenReturn(true);

        // Default: dash-service delivers to an open session
        when(notificationDispatchPort.dispatch(any(), any()))
                .thenReturn(
                        new NotificationDispatchPort.DispatchOutcome(
                                NotificationDispatchPort.DispatchOutcome.DELIVERED));

        // Default: audit trail is a no-op
        doNothing().when(auditTrailPort).record(any());
    }

    /**
     * After each scenario: purge all rows inserted during the scenario.
     *
     * <p>The test thread may have had its {@link TenantContextHolder} cleared by the scenario (e.g.
     * if a step explicitly called {@code TenantContextHolder.clear()}). Re-bind before the deletes
     * so {@code MultiTenantConnectionProvider} routes to the correct tenant schema. Clear at the
     * very end so the thread is clean for the next scenario's {@code @Before}.
     */
    @After
    public void afterScenario() {
        try {
            // E2E-022: ensure deletes route to the same schema that holds the rows.
            TenantContextHolder.set("empresa_demo");
            reportIdempotencyJpaRepository.deleteAll();
            notificationDispatchJpaRepository.deleteAll();
            eventoJpaRepository.deleteAll();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
