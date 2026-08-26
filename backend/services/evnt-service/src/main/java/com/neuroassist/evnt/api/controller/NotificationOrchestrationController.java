package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.NotificationOrchestrationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for the dispatch retry sweep.
 *
 * <p>The sweep normally runs on a schedule ({@code NotificationDispatchScheduler}); this endpoint
 * exists so an operator can force it after an incident without waiting for the next tick.
 *
 * <p>Both methods here used to throw {@code UnsupportedOperationException}, and I mis-filed them as
 * dead scaffolding during Sessão 4 — the dispatch itself happens in {@code ReportIngressService},
 * so the controller looked inert. It was not: the retry it names is the only thing that would ever
 * re-send an alarm left {@code QUEUED} or {@code NO_SESSION}, and without it those alarms were
 * durable and permanently undelivered (C-45).
 */
@Slf4j
@RestController
@RequestMapping("/evnt-service/notification-orchestration")
@RequiredArgsConstructor
public class NotificationOrchestrationController {

    private final NotificationOrchestrationUseCase notificationOrchestrationUseCase;

    /**
     * Runs one batch of the retry sweep for the caller's tenant, bound by {@code
     * TenantContextFilter} from the JWT.
     */
    @PostMapping("/retry-sweep")
    @PreAuthorize("hasAuthority('evnt.notification-orchestration.reprocessar')")
    public ResponseEntity<NotificationOrchestrationUseCase.SweepResult> retrySweep() {
        return ResponseEntity.ok(notificationOrchestrationUseCase.retrySweep());
    }
}
