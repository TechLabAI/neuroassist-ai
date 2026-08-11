package com.neuroassist.gest.application.port.in;

import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import java.util.Optional;
import java.util.UUID;

/** Inbound port — DetectionProfile use cases. */
public interface DetectionProfileUseCase {

    DetectionProfileResponse publish(DetectionProfileRequest request);

    Optional<DetectionProfileResponse> findActive();

    Optional<DetectionProfileResponse> findByVersion(long version);

    Optional<DetectionProfileResponse> findForDevice(UUID deviceId);
}
