package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.GetDeviceProfileUseCase;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.application.port.out.GetFullDetectionProfilePort;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service — device detection profile retrieval (EDGE-022, P1).
 *
 * <p>NOT {@code @Transactional}: this use case has no DB writes. The repository method executes in
 * its own transaction; the Feign call is outside any transaction context.
 *
 * <p>Invariant order (RULE-07 first):
 *
 * <ol>
 *   <li>{@link TenantContextHolder#require()} — binds the Hibernate search_path.
 *   <li>Load device — tenant-safe lookup.
 *   <li>Verify pairing state — not paired → 403 {@code edge.device-not-paired}.
 *   <li>Fetch full profile — degraded GEST → 503 {@code edge.profile-unavailable}. A degraded
 *       result MUST NOT silently degrade to an empty profile — that is the worst failure mode of
 *       this product (station runs without detecting anything, appearing healthy).
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetDeviceProfileService implements GetDeviceProfileUseCase {

    private final DispositivoEdgeRepositoryPort deviceRepo;
    private final GetFullDetectionProfilePort profilePort;

    @Override
    public DeviceProfileResult getDeviceProfile(UUID deviceId, String ifNoneMatch) {
        // RULE-07: TenantContextHolder.require() must be the FIRST call in every public method
        // that touches empresa data. Binds the Hibernate search_path before any query.
        String tenantId = TenantContextHolder.require();

        Optional<DispositivoEdge> deviceOpt = deviceRepo.findByIdAndTenantId(deviceId, tenantId);
        if (deviceOpt.isEmpty()) {
            throw new CodedException(
                    "edge.device-not-found",
                    "Device " + deviceId + " not found in tenant " + tenantId);
        }

        DispositivoEdge device = deviceOpt.get();
        if (device.pairingState() != PairingState.paired) {
            throw new CodedException(
                    "edge.device-not-paired",
                    "Device " + deviceId + " is not paired (state: " + device.pairingState() + ")");
        }

        GetFullDetectionProfilePort.ProfileResult portResult =
                profilePort.getProfileForDevice(deviceId, ifNoneMatch);

        return switch (portResult) {
            case GetFullDetectionProfilePort.ProfileResult.NotModified ignored ->
                    DeviceProfileResult.notModified();
            case GetFullDetectionProfilePort.ProfileResult.Degraded ignored -> {
                log.error(
                        "GEST unavailable — refusing to serve empty profile for deviceId='{}'"
                                + " (tenant='{}'). Surfacing edge.profile-unavailable.",
                        deviceId,
                        tenantId);
                throw new CodedException(
                        "edge.profile-unavailable",
                        "GEST is unavailable — cannot serve detection profile."
                                + " An empty profile would make the station appear healthy"
                                + " while detecting nothing.");
            }
            case GetFullDetectionProfilePort.ProfileResult.Ok ok ->
                    DeviceProfileResult.ok(mapToUseCaseData(ok.profile()));
        };
    }

    // -------------------------------------------------------------------------
    // Mapping: outbound port data → use-case result data
    // (GestDetectionProfileView is fully abstracted away by the time we get here)
    // -------------------------------------------------------------------------

    private static GetDeviceProfileUseCase.ProfileData mapToUseCaseData(
            GetFullDetectionProfilePort.ProfileData src) {
        return new GetDeviceProfileUseCase.ProfileData(
                src.pacienteId(),
                src.version(),
                src.catalogoDegradado(),
                src.etag(),
                mapThresholds(src.thresholds()),
                mapPrimitives(src.primitives()));
    }

    private static GetDeviceProfileUseCase.ThresholdData mapThresholds(
            GetFullDetectionProfilePort.ThresholdTableData src) {
        if (src == null) {
            return null;
        }
        return new GetDeviceProfileUseCase.ThresholdData(
                mapBand(src.baixa()), mapBand(src.media()), mapBand(src.alta()));
    }

    private static GetDeviceProfileUseCase.BandData mapBand(
            GetFullDetectionProfilePort.BandData src) {
        if (src == null) {
            return null;
        }
        return new GetDeviceProfileUseCase.BandData(
                src.earClose(),
                src.earOpen(),
                src.earOtherMax(),
                src.blinkMinFrames(),
                src.doubleBlinkWindowMs(),
                src.briRaiseOn(),
                src.briRaiseOff(),
                src.briOtherMax(),
                src.browMinFrames());
    }

    private static List<GetDeviceProfileUseCase.PrimitiveData> mapPrimitives(
            List<GetFullDetectionProfilePort.PrimitiveData> src) {
        if (src == null) {
            return List.of();
        }
        return src.stream()
                .map(
                        p ->
                                new GetDeviceProfileUseCase.PrimitiveData(
                                        p.tipoGestoId(),
                                        p.nome(),
                                        p.engineFeatureRef(),
                                        p.sensibilidade(),
                                        p.origem()))
                .toList();
    }
}
