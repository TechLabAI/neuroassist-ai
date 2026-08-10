package com.neuroassist.gest.application.service;

import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.TipoGesto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DetectionProfileService implements DetectionProfileUseCase {

    private final TipoGestoRepositoryPort tipoGestoRepositoryPort;
    private final DetectionProfileRepositoryPort detectionProfileRepositoryPort;
    private final DetectionProfilePrimitiveRepositoryPort detectionProfilePrimitiveRepositoryPort;

    @Override
    public DetectionProfile publish() {
        log.info("Publishing new detection profile");
        List<TipoGesto> supported = tipoGestoRepositoryPort.findByState("SUPPORTED");

        int nextVersion = detectionProfileRepositoryPort.findActive()
                .map(p -> p.version() + 1)
                .orElse(1);

        detectionProfileRepositoryPort.deactivateAll();

        DetectionProfile profile = new DetectionProfile(
                UUID.randomUUID(), nextVersion, true, "1.0.0", Instant.now());
        DetectionProfile saved = detectionProfileRepositoryPort.save(profile);

        for (TipoGesto tg : supported) {
            DetectionProfilePrimitive prim = new DetectionProfilePrimitive(
                    UUID.randomUUID(), saved.id(), tg.id(), tg.nome(), tg.engineFeatureRef());
            detectionProfilePrimitiveRepositoryPort.save(prim);
        }

        log.info("Published detection profile version={}", saved.version());
        return saved;
    }

    @Override
    public Optional<DetectionProfile> getActive() {
        return detectionProfileRepositoryPort.findActive();
    }

    @Override
    public Optional<DetectionProfile> findByVersion(int version) {
        return detectionProfileRepositoryPort.findByVersion(version);
    }

    @Override
    public Optional<ActiveWithPrimitives> getActiveWithPrimitives() {
        return detectionProfileRepositoryPort.findActive()
                .map(p -> {
                    List<DetectionProfilePrimitive> prims =
                            detectionProfilePrimitiveRepositoryPort.findByProfileId(p.id());
                    return new ActiveWithPrimitives(p, prims);
                });
    }
}
