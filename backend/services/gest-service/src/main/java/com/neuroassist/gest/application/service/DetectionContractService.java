package com.neuroassist.gest.application.service;

import com.neuroassist.gest.api.dto.DetectionContractRequest;
import com.neuroassist.gest.api.dto.DetectionContractResponse;
import com.neuroassist.gest.application.port.in.DetectionContractUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stateless application service for RecognizedCommandReport validation (GEST-010). Implements the
 * frozen contract v1.0.0 that the edge must satisfy when posting gesture events.
 *
 * <p>LGPD: forbidden fields (raw biometric data) are rejected at this boundary — no processing, no
 * logging, no persistence of biometric payloads. Only the gesture ID and aggregate metrics are
 * permitted.
 *
 * <p>This service is intentionally stateless — no DB interaction, no events emitted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DetectionContractService implements DetectionContractUseCase {

    /**
     * Frozen contract descriptor for {@code RecognizedCommandReport} (contract v1.0.0).
     *
     * <ul>
     *   <li><b>requiredFields</b>: must all be present and non-null in a valid report.
     *   <li><b>forbiddenFields</b>: raw biometric data — presence rejects the report (LGPD).
     * </ul>
     */
    public record ContractDescriptorV1(
            String version, List<String> requiredFields, List<String> forbiddenFields) {}

    private static final ContractDescriptorV1 CONTRACT_DESCRIPTOR =
            new ContractDescriptorV1(
                    "v1.0.0",
                    List.of("gestureId", "confidence", "windowMs"),
                    List.of("video", "image", "landmark", "base64", "pixels", "frame"));

    /** Returns the static contract descriptor (version + allowed/forbidden field catalogue). */
    @Override
    public Object getContractDescriptor() {
        return CONTRACT_DESCRIPTOR;
    }

    /**
     * Validates a {@code RecognizedCommandReport} against the frozen contract v1.0.0.
     *
     * <ul>
     *   <li>Forbidden fields (raw biometrics) → rejected immediately (LGPD).
     *   <li>Required fields missing or null → rejected.
     * </ul>
     *
     * @return {@link DetectionContractResponse} with {@code valid=true} and empty errors list on
     *     success; otherwise {@code valid=false} with one error message per violation.
     */
    @Override
    public DetectionContractResponse validateReport(DetectionContractRequest request) {
        Map<String, Object> report = request.report() != null ? request.report() : Map.of();
        List<String> errors = new ArrayList<>();

        // Check LGPD-forbidden fields — biometric raw data must never cross this boundary
        for (String forbidden : CONTRACT_DESCRIPTOR.forbiddenFields()) {
            if (report.containsKey(forbidden)) {
                errors.add(
                        "Field '"
                                + forbidden
                                + "' is forbidden by contract v1.0.0 (LGPD — raw biometric data)");
            }
        }

        // Check required fields
        for (String required : CONTRACT_DESCRIPTOR.requiredFields()) {
            if (!report.containsKey(required) || report.get(required) == null) {
                errors.add("Required field '" + required + "' is missing or null");
            }
        }

        boolean valid = errors.isEmpty();
        if (!valid) {
            log.debug("Contract validation failed with {} error(s)", errors.size());
        }
        return new DetectionContractResponse(valid, errors);
    }
}
