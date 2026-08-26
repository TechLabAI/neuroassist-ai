package com.neuroassist.gest.application.port.out;

import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port — DetectionProfilePrimitive persistence. Primitives are snapshots taken at
 * publish-time and are immutable after creation. Multi-tenancy is implicit via schema routing.
 * Implemented by DetectionProfilePrimitiveRepositoryAdapter (infrastructure layer, P3).
 */
public interface DetectionProfilePrimitiveRepositoryPort {

    List<DetectionProfilePrimitive> saveAll(List<DetectionProfilePrimitive> primitives);

    List<DetectionProfilePrimitive> findByProfileId(UUID profileId);
}
