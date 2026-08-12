package com.neuroassist.gest.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.gest.api.dto.DetectionContractRequest;
import com.neuroassist.gest.api.dto.DetectionContractResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DetectionContractService (Fase 8 — P2, GEST-010).
 *
 * <p>Validates the frozen contract v1.0.0 enforcement: required fields, LGPD forbidden fields.
 * Service is instantiated directly (no Spring context — stateless pure logic).
 */
class DetectionContractServiceTest {

    private DetectionContractService contractService;

    @BeforeEach
    void setUp() {
        contractService = new DetectionContractService();
    }

    // ── getContractDescriptor ────────────────────────────────────────────────

    @Test
    void getContractDescriptor_returnsContractDescriptorV1() {
        Object descriptor = contractService.getContractDescriptor();
        assertThat(descriptor).isNotNull();
        assertThat(descriptor).isInstanceOf(DetectionContractService.ContractDescriptorV1.class);
    }

    @Test
    void getContractDescriptor_version_isV1dot0dot0() {
        DetectionContractService.ContractDescriptorV1 v1 =
                (DetectionContractService.ContractDescriptorV1)
                        contractService.getContractDescriptor();
        assertThat(v1.version()).isEqualTo("v1.0.0");
    }

    @Test
    void getContractDescriptor_requiredFields_containsGestureIdConfidenceWindowMs() {
        DetectionContractService.ContractDescriptorV1 v1 =
                (DetectionContractService.ContractDescriptorV1)
                        contractService.getContractDescriptor();
        assertThat(v1.requiredFields())
                .containsExactlyInAnyOrder("gestureId", "confidence", "windowMs");
    }

    @Test
    void getContractDescriptor_forbiddenFields_containsAllBiometricFields() {
        DetectionContractService.ContractDescriptorV1 v1 =
                (DetectionContractService.ContractDescriptorV1)
                        contractService.getContractDescriptor();
        assertThat(v1.forbiddenFields())
                .containsAll(
                        java.util.List.of(
                                "video", "image", "landmark", "base64", "pixels", "frame"));
    }

    // ── validateReport — valid reports ───────────────────────────────────────

    @Test
    void validateReport_validReport_returnsValid() {
        Map<String, Object> report =
                Map.of("gestureId", "piscar-esquerdo", "confidence", 0.95, "windowMs", 200);

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isTrue();
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void validateReport_validReportWithExtraFields_returnsValid() {
        // Extra fields that are not forbidden are allowed
        Map<String, Object> report =
                Map.of(
                        "gestureId",
                        "piscar",
                        "confidence",
                        0.9,
                        "windowMs",
                        300,
                        "sessionId",
                        "abc-123");

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isTrue();
    }

    // ── validateReport — forbidden fields (LGPD) ────────────────────────────

    @Test
    void validateReport_imageField_returnsInvalidWithImageError() {
        Map<String, Object> report = new HashMap<>();
        report.put("gestureId", "piscar");
        report.put("confidence", 0.95);
        report.put("windowMs", 200);
        report.put("image", "base64encodeddata");

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> e.contains("image"));
    }

    @Test
    void validateReport_videoField_returnsInvalid() {
        Map<String, Object> report = new HashMap<>();
        report.put("gestureId", "piscar");
        report.put("confidence", 0.9);
        report.put("windowMs", 200);
        report.put("video", "rawvideobytes");

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> e.contains("video"));
    }

    @Test
    void validateReport_landmarkField_returnsInvalid() {
        Map<String, Object> report = new HashMap<>();
        report.put("gestureId", "piscar");
        report.put("confidence", 0.9);
        report.put("windowMs", 200);
        report.put("landmark", "[0.1, 0.2]");

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> e.contains("landmark"));
    }

    @Test
    void validateReport_multipleForbiddenFields_allReported() {
        Map<String, Object> report = new HashMap<>();
        report.put("gestureId", "piscar");
        report.put("confidence", 0.9);
        report.put("windowMs", 200);
        report.put("video", "data");
        report.put("frame", "raw");

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).hasSize(2);
        assertThat(response.errors()).anyMatch(e -> e.contains("video"));
        assertThat(response.errors()).anyMatch(e -> e.contains("frame"));
    }

    // ── validateReport — missing required fields ─────────────────────────────

    @Test
    void validateReport_missingWindowMs_returnsInvalid() {
        Map<String, Object> report = Map.of("gestureId", "piscar", "confidence", 0.95);

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> e.contains("windowMs"));
    }

    @Test
    void validateReport_missingGestureId_returnsInvalid() {
        Map<String, Object> report = Map.of("confidence", 0.95, "windowMs", 200);

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> e.contains("gestureId"));
    }

    @Test
    void validateReport_allRequiredMissing_reportsAllThreeErrors() {
        // Only unknown extras — all 3 required fields missing
        Map<String, Object> report = Map.of("sessionId", "xyz");

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).hasSize(3);
    }

    @Test
    void validateReport_nullReport_returnsInvalidForAllRequiredFields() {
        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(null));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).hasSize(3); // gestureId, confidence, windowMs all missing
    }

    @Test
    void validateReport_forbiddenAndMissing_errorsContainBoth() {
        Map<String, Object> report = new HashMap<>();
        report.put("gestureId", "piscar");
        // confidence and windowMs missing + forbidden field present
        report.put("base64", "aGVsbG8=");

        DetectionContractResponse response =
                contractService.validateReport(new DetectionContractRequest(report));

        assertThat(response.valid()).isFalse();
        // base64 forbidden + confidence missing + windowMs missing = 3 errors
        assertThat(response.errors()).hasSize(3);
        assertThat(response.errors()).anyMatch(e -> e.contains("base64"));
        assertThat(response.errors()).anyMatch(e -> e.contains("confidence"));
        assertThat(response.errors()).anyMatch(e -> e.contains("windowMs"));
    }
}
