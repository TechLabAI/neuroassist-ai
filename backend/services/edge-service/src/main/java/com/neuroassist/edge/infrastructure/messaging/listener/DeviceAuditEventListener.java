package com.neuroassist.edge.infrastructure.messaging.listener;

import com.neuroassist.edge.domain.event.DispositivoDespareado;
import com.neuroassist.edge.domain.event.DispositivoFicouOffline;
import com.neuroassist.edge.domain.event.DispositivoPareado;
import com.neuroassist.edge.domain.event.DispositivoRegistrado;
import com.neuroassist.edge.domain.event.DispositivoVoltouOnline;
import com.neuroassist.edge.domain.event.HeartbeatRecebido;
import com.neuroassist.edge.domain.event.ProfileDriftDetected;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DeviceAuditEventListener — EDGE-016 (P6). Handles all seven EDGE domain events in-process, after
 * the originating transaction commits. Uses @TransactionalEventListener(AFTER_COMMIT) so that:
 *
 * <ul>
 *   <li>A transaction rollback does NOT trigger any listener method.
 *   <li>A listener failure is caught and logged — it NEVER propagates to the original transaction
 *       (which has already committed).
 * </ul>
 *
 * <p>Listener methods are intentionally thin: log + metric stub only. No state-write operations are
 * allowed here (ADR-006: liveness is never written; ADR-005: audit rows are written only by
 * application services).
 *
 * <p>DI via constructor is not applicable for a no-dependency listener. @RequiredArgsConstructor
 * would generate an empty constructor — Lombok is omitted for clarity; the default constructor is
 * used.
 */
@Slf4j
@Component
public class DeviceAuditEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispositivoRegistrado(DispositivoRegistrado event) {
        try {
            log.info(
                    "[EDGE-EVENT] DispositivoRegistrado deviceId='{}' tenant='{}' label='{}' at='{}'",
                    event.deviceId(),
                    event.tenantRef(),
                    event.label(),
                    event.at());
        } catch (Exception ex) {
            log.error(
                    "[EDGE-EVENT] Listener error for DispositivoRegistrado deviceId='{}': {}",
                    event.deviceId(),
                    ex.getMessage(),
                    ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispositivoPareado(DispositivoPareado event) {
        try {
            log.info(
                    "[EDGE-EVENT] DispositivoPareado deviceId='{}' tenant='{}' pacienteId='{}' actor='{}' at='{}'",
                    event.deviceId(),
                    event.tenantRef(),
                    event.pacienteId(),
                    event.actor(),
                    event.at());
        } catch (Exception ex) {
            log.error(
                    "[EDGE-EVENT] Listener error for DispositivoPareado deviceId='{}': {}",
                    event.deviceId(),
                    ex.getMessage(),
                    ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispositivoDespareado(DispositivoDespareado event) {
        try {
            log.info(
                    "[EDGE-EVENT] DispositivoDespareado deviceId='{}' tenant='{}' reason='{}' actor='{}' at='{}'",
                    event.deviceId(),
                    event.tenantRef(),
                    event.reason(),
                    event.actor(),
                    event.at());
        } catch (Exception ex) {
            log.error(
                    "[EDGE-EVENT] Listener error for DispositivoDespareado deviceId='{}': {}",
                    event.deviceId(),
                    ex.getMessage(),
                    ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHeartbeatRecebido(HeartbeatRecebido event) {
        try {
            log.debug(
                    "[EDGE-EVENT] HeartbeatRecebido deviceId='{}' tenant='{}' agentVersion='{}' runningProfileVersion={} at='{}'",
                    event.deviceId(),
                    event.tenantRef(),
                    event.agentVersion(),
                    event.runningProfileVersion(),
                    event.at());
        } catch (Exception ex) {
            log.error(
                    "[EDGE-EVENT] Listener error for HeartbeatRecebido deviceId='{}': {}",
                    event.deviceId(),
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * DispositivoFicouOffline is derived at read-time (ADR-006) — this listener MUST only
     * log/metric, never write state. It fires on every liveness poll that finds the device offline.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispositivoFicouOffline(DispositivoFicouOffline event) {
        try {
            log.info(
                    "[EDGE-EVENT] DispositivoFicouOffline deviceId='{}' tenant='{}' ultimaSincronizacao='{}' at='{}'",
                    event.deviceId(),
                    event.tenantRef(),
                    event.ultimaSincronizacao(),
                    event.at());
        } catch (Exception ex) {
            log.error(
                    "[EDGE-EVENT] Listener error for DispositivoFicouOffline deviceId='{}': {}",
                    event.deviceId(),
                    ex.getMessage(),
                    ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispositivoVoltouOnline(DispositivoVoltouOnline event) {
        try {
            log.info(
                    "[EDGE-EVENT] DispositivoVoltouOnline deviceId='{}' tenant='{}' at='{}'",
                    event.deviceId(),
                    event.tenantRef(),
                    event.at());
        } catch (Exception ex) {
            log.error(
                    "[EDGE-EVENT] Listener error for DispositivoVoltouOnline deviceId='{}': {}",
                    event.deviceId(),
                    ex.getMessage(),
                    ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileDriftDetected(ProfileDriftDetected event) {
        try {
            log.warn(
                    "[EDGE-EVENT] ProfileDriftDetected deviceId='{}' tenant='{}' runningVersion={} currentVersion={} at='{}'",
                    event.deviceId(),
                    event.tenantRef(),
                    event.runningVersion(),
                    event.currentVersion(),
                    event.at());
        } catch (Exception ex) {
            log.error(
                    "[EDGE-EVENT] Listener error for ProfileDriftDetected deviceId='{}': {}",
                    event.deviceId(),
                    ex.getMessage(),
                    ex);
        }
    }
}
