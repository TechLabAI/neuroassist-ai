package com.neuroassist.edge.application.port.out;

import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.domain.model.TransitionKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositivoEdgeTransitionRepositoryPort {

    /** Append-only save. No update/delete operations — see data-model.md §2.4. */
    DispositivoEdgeTransition save(DispositivoEdgeTransition entity);

    Optional<DispositivoEdgeTransition> findById(UUID id);

    List<DispositivoEdgeTransition> findByTenantId(String tenantId);

    /** Ordered audit trail for a specific device. */
    List<DispositivoEdgeTransition> findByDispositivoEdgeIdOrderByAtDesc(UUID deviceId, int limit);

    /**
     * Find the latest liveness transition for a device. Used to detect crossing (online→offline /
     * offline→online) during heartbeat.
     */
    Optional<DispositivoEdgeTransition> findLatestLivenessByDeviceId(
            UUID deviceId, TransitionKind kind);

    /**
     * Idempotency guard for online→offline transition writing. Returns {@code true} if a liveness
     * transition with {@code to_state='offline'} already exists for this device with {@code at >
     * after}. LivenessService uses this to ensure exactly one online→offline row per staleness
     * epoch (identified by {@code after = device.ultimaSincronizacao()}).
     */
    boolean existsLivenessTransitionToOfflineAfter(UUID deviceId, Instant after);
}
