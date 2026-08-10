package com.neuroassist.gest.application.service;

import com.neuroassist.gest.application.port.in.DetectionContractUseCase;
import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DetectionContractService implements DetectionContractUseCase {

    private final DetectionProfileRepositoryPort detectionProfileRepositoryPort;
    private final DetectionProfilePrimitiveRepositoryPort detectionProfilePrimitiveRepositoryPort;

    @Override
    public ValidationResult validateReport(ValidateReportCommand command) {
        log.debug("Validating report for profileVersion={}", command.profileVersion());

        DetectionProfile profile = detectionProfileRepositoryPort
                .findByVersion(command.profileVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "DetectionProfile not found for version: " + command.profileVersion()));

        List<DetectionProfilePrimitive> primitives =
                detectionProfilePrimitiveRepositoryPort.findByProfileId(profile.id());

        Set<String> allowedIds = primitives.stream()
                .map(DetectionProfilePrimitive::tipoGestoId)
                .collect(Collectors.toSet());

        for (String tipoGestoId : command.tipoGestoIds()) {
            if (!allowedIds.contains(tipoGestoId)) {
                log.warn("tipoGestoId={} not found in profile version={}", tipoGestoId,
                        command.profileVersion());
                throw new IllegalArgumentException(
                        "tipoGestoId not in profile: " + tipoGestoId);
            }
        }

        log.info("Report validated successfully for profileVersion={}", command.profileVersion());
        return new ValidationResult(true, profile.contractVersion());
    }
}
