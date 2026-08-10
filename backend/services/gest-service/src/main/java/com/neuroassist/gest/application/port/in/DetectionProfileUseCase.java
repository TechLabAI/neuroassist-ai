package com.neuroassist.gest.application.port.in;

import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import java.util.List;
import java.util.Optional;

public interface DetectionProfileUseCase {

    DetectionProfile publish();

    Optional<DetectionProfile> getActive();

    Optional<DetectionProfile> findByVersion(int version);

    Optional<ActiveWithPrimitives> getActiveWithPrimitives();

    record ActiveWithPrimitives(DetectionProfile profile, List<DetectionProfilePrimitive> primitives) {}
}
