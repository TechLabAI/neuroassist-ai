package com.neuroassist.edge.application.port.out;

import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.domain.model.TransitionKind;
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
}
