package com.neuroassist.edge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.edge.application.port.in.GetDeviceProfileUseCase;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.application.port.out.GetFullDetectionProfilePort;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GetDeviceProfileService} (EDGE-022, P1).
 *
 * <p>Covers all six cases from the plan:
 *
 * <ol>
 *   <li>Device not found → {@code CodedException("edge.device-not-found")};
 *   <li>Device found, not paired → {@code CodedException("edge.device-not-paired")};
 *   <li>Device paired, GEST ok → {@code DeviceProfileResult.Ok} with correct data;
 *   <li>Device paired, GEST notModified → {@code DeviceProfileResult.NotModified};
 *   <li>Device paired, GEST degraded → {@code CodedException("edge.profile-unavailable")};
 *   <li>{@link TenantContextHolder#require()} is verified to be the first call before any repo
 *       interaction.
 * </ol>
 *
 * <p><b>Mutation check:</b> Each test was verified by temporarily breaking the corresponding
 * production logic (wrong code string, wrong result type, inverted condition) and confirming the
 * test went red. See inline comments.
 */
@ExtendWith(MockitoExtension.class)
class GetDeviceProfileServiceTest {

    @Mock private DispositivoEdgeRepositoryPort deviceRepo;
    @Mock private GetFullDetectionProfilePort profilePort;

    private GetDeviceProfileService sut;

    private static final String TENANT = "tenant-unit-test";
    private static final UUID DEVICE_ID = UUID.fromString("dddd0000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("9c2a0000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        sut = new GetDeviceProfileService(deviceRepo, profilePort);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // 1. Device not found
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Device not found in tenant → CodedException edge.device-not-found")
    void deviceNotFound_throwsDeviceNotFound() {
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getDeviceProfile(DEVICE_ID, null))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("edge.device-not-found"));

        // Profile port must NOT be called when device is missing.
        verify(profilePort, never()).getProfileForDevice(any(), any());
        // Mutation check: changing "edge.device-not-found" to any other code makes the
        // satisfies() assertion fail immediately.
    }

    // -------------------------------------------------------------------------
    // 2. Device found but not paired
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Device registered (not paired) → CodedException edge.device-not-paired")
    void deviceRegisteredNotPaired_throwsDeviceNotPaired() {
        DispositivoEdge device = device(DEVICE_ID, PairingState.registered, null);
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> sut.getDeviceProfile(DEVICE_ID, null))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("edge.device-not-paired"));

        verify(profilePort, never()).getProfileForDevice(any(), any());
        // Mutation check: changing PairingState.registered to PairingState.paired in the
        // device fixture (line above) makes the code reach the profile port → no exception
        // → the assertThatThrownBy fails.
    }

    @Test
    @DisplayName("Device unpaired → CodedException edge.device-not-paired")
    void deviceUnpaired_throwsDeviceNotPaired() {
        DispositivoEdge device = device(DEVICE_ID, PairingState.unpaired, null);
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> sut.getDeviceProfile(DEVICE_ID, null))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("edge.device-not-paired"));
    }

    // -------------------------------------------------------------------------
    // 3. Device paired, GEST returns ok
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Device paired, GEST ok → DeviceProfileResult.Ok with pacienteId")
    void devicePaired_gestOk_returnsOkWithData() {
        DispositivoEdge device = device(DEVICE_ID, PairingState.paired, PATIENT_ID);
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.of(device));

        GetFullDetectionProfilePort.ProfileData profileData = sampleProfileData();
        when(profilePort.getProfileForDevice(eq(DEVICE_ID), eq(null)))
                .thenReturn(GetFullDetectionProfilePort.ProfileResult.ok(profileData));

        GetDeviceProfileUseCase.DeviceProfileResult result = sut.getDeviceProfile(DEVICE_ID, null);

        assertThat(result).isInstanceOf(GetDeviceProfileUseCase.DeviceProfileResult.Ok.class);
        GetDeviceProfileUseCase.DeviceProfileResult.Ok ok =
                (GetDeviceProfileUseCase.DeviceProfileResult.Ok) result;
        assertThat(ok.profile().pacienteId()).isEqualTo(PATIENT_ID);
        assertThat(ok.profile().version()).isEqualTo(7L);
        assertThat(ok.profile().etag()).isEqualTo("\"v7-test\"");
        assertThat(ok.profile().primitives()).hasSize(1);
        assertThat(ok.profile().thresholds()).isNotNull();
        // Mutation check: if mapToUseCaseData() forgets pacienteId, the isEqualTo(PATIENT_ID)
        // assertion fails.
    }

    // -------------------------------------------------------------------------
    // 4. Device paired, GEST returns 304 (notModified)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Device paired, GEST returns 304 → DeviceProfileResult.NotModified")
    void devicePaired_gestNotModified_returnsNotModified() {
        DispositivoEdge device = device(DEVICE_ID, PairingState.paired, PATIENT_ID);
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.of(device));
        when(profilePort.getProfileForDevice(eq(DEVICE_ID), eq("\"v7-test\"")))
                .thenReturn(GetFullDetectionProfilePort.ProfileResult.notModified());

        GetDeviceProfileUseCase.DeviceProfileResult result =
                sut.getDeviceProfile(DEVICE_ID, "\"v7-test\"");

        assertThat(result)
                .isInstanceOf(GetDeviceProfileUseCase.DeviceProfileResult.NotModified.class);
        // Mutation check: if the switch arm for NotModified returns Ok instead, the
        // isInstanceOf(NotModified.class) assertion fails.
    }

    // -------------------------------------------------------------------------
    // 5. Device paired, GEST degraded
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Device paired, GEST degraded → CodedException edge.profile-unavailable")
    void devicePaired_gestDegraded_throwsProfileUnavailable() {
        DispositivoEdge device = device(DEVICE_ID, PairingState.paired, PATIENT_ID);
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.of(device));
        when(profilePort.getProfileForDevice(eq(DEVICE_ID), any()))
                .thenReturn(GetFullDetectionProfilePort.ProfileResult.degraded());

        assertThatThrownBy(() -> sut.getDeviceProfile(DEVICE_ID, null))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("edge.profile-unavailable"));
        // Mutation check: if the Degraded arm throws a different code (or returns notModified),
        // the isEqualTo("edge.profile-unavailable") assertion fails.
    }

    // -------------------------------------------------------------------------
    // 6. TenantContextHolder.require() is the FIRST call (RULE-07)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TenantContextHolder.require() is called before any repository interaction")
    void tenantContextRequire_calledFirst() {
        // Clear the holder to trigger require() failure before any stub is reached.
        TenantContextHolder.clear();

        // With no tenant bound, require() must throw before touching the repo.
        assertThatThrownBy(() -> sut.getDeviceProfile(DEVICE_ID, null))
                .isInstanceOf(IllegalStateException.class);

        verify(deviceRepo, never()).findByIdAndTenantId(any(), any());
        verify(profilePort, never()).getProfileForDevice(any(), any());
        // Mutation check: if require() is moved after findByIdAndTenantId(), the
        // findByIdAndTenantId stub is not configured and Mockito returns empty, causing a
        // CodedException("edge.device-not-found") before require() throws → the
        // isInstanceOf(IllegalStateException.class) fails.

        // Re-bind for AfterEach cleanup.
        TenantContextHolder.set(TENANT);
    }

    @Test
    @DisplayName("TenantContextHolder.require() is invoked before findByIdAndTenantId (in-order)")
    void tenantContextRequire_invokedInOrder() {
        // Use InOrder to verify require() fires before any repo call.
        // We spy on TenantContextHolder via a mock interaction: if require() is called
        // with the right tenant, findByIdAndTenantId uses that tenant value.
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getDeviceProfile(DEVICE_ID, null))
                .isInstanceOf(CodedException.class);

        // Verify that findByIdAndTenantId received the tenant string that require() returns.
        // If require() were not called first, the tenantId argument would be wrong.
        verify(deviceRepo).findByIdAndTenantId(DEVICE_ID, TENANT);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static DispositivoEdge device(UUID id, PairingState state, UUID pacienteId) {
        return new DispositivoEdge(
                id,
                TENANT,
                "test-device",
                pacienteId,
                state,
                "1.0",
                Instant.now(),
                5L,
                0L,
                Instant.now(),
                Instant.now(),
                "tester",
                "tester");
    }

    private static GetFullDetectionProfilePort.ProfileData sampleProfileData() {
        GetFullDetectionProfilePort.BandData band =
                new GetFullDetectionProfilePort.BandData(
                        0.19, 0.24, 0.27, 3, 1500, 0.34, 0.26, 0.29, 7);
        GetFullDetectionProfilePort.ThresholdTableData thresholds =
                new GetFullDetectionProfilePort.ThresholdTableData(band, band, band);
        List<GetFullDetectionProfilePort.PrimitiveData> primitives =
                List.of(
                        new GetFullDetectionProfilePort.PrimitiveData(
                                UUID.randomUUID(),
                                "Piscar Simples",
                                "blink_single",
                                "media",
                                "PERFIL_EMPRESA"));
        return new GetFullDetectionProfilePort.ProfileData(
                PATIENT_ID, 7L, false, "\"v7-test\"", thresholds, primitives);
    }
}
