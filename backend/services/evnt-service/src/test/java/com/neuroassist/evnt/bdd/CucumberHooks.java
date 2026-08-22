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
import io.cucumber.java.After;
import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;

/**
 * Cucumber lifecycle hooks: reset mocks before each scenario and clean database after. Singleton
 * Spring bean — injected repositories and mocked ports are reused across scenarios.
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
     * Before each scenario: reset all mocks and set sensible defaults so individual Given steps
     * only need to override what's specific to their scenario.
     */
    @Before
    public void beforeScenario() {
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

    /** After each scenario: purge all rows inserted during the scenario. */
    @After
    public void afterScenario() {
        reportIdempotencyJpaRepository.deleteAll();
        notificationDispatchJpaRepository.deleteAll();
        eventoJpaRepository.deleteAll();
    }
}
