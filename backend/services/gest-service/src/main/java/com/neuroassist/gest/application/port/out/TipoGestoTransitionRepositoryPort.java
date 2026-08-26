package com.neuroassist.gest.application.port.out;

import com.neuroassist.gest.domain.model.TipoGestoTransition;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port — TipoGestoTransition persistence. Records are append-only (ADR-006 invariant #4 —
 * audit log is immutable). Multi-tenancy is implicit via schema routing. Implemented by
 * TipoGestoTransitionRepositoryAdapter (infrastructure layer, P3).
 */
public interface TipoGestoTransitionRepositoryPort {

    TipoGestoTransition save(TipoGestoTransition transition);

    List<TipoGestoTransition> findByTipoGestoId(UUID tipoGestoId);
}
