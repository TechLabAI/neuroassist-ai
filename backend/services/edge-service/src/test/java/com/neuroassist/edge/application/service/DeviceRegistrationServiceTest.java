package com.neuroassist.edge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import com.neuroassist.edge.application.port.out.DispositivoCredentialRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeTransitionRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEnrollmentRepositoryPort;
import com.neuroassist.edge.domain.model.CredentialStatus;
import com.neuroassist.edge.domain.model.DispositivoCredential;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.domain.model.DispositivoEnrollment;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.metrics.EdgeMetrics;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceRegistrationServiceTest {

    @Mock private DispositivoEdgeRepositoryPort deviceRepo;
    @Mock private DispositivoEnrollmentRepositoryPort enrollmentRepo;
    @Mock private DispositivoCredentialRepositoryPort credentialRepo;
    @Mock private DispositivoEdgeTransitionRepositoryPort transitionRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EdgeMetrics edgeMetrics;

    private final DispositivoEdgeDomainService domainService = new DispositivoEdgeDomainService();
    private DeviceRegistrationService sut;

    private static final String TENANT = "tenant-test";

    @BeforeEach
    void setUp() {
        sut =
                new DeviceRegistrationService(
                        deviceRepo,
                        enrollmentRepo,
                        credentialRepo,
                        transitionRepo,
                        domainService,
                        eventPublisher,
                        edgeMetrics);
        ReflectionTestUtils.setField(sut, "enrollmentTtlSeconds", 900L);
        ReflectionTestUtils.setField(sut, "heartbeatIntervalSeconds", 30L);
        ReflectionTestUtils.setField(
                sut,
                "acsTokenEndpoint",
                "http://localhost:8180/realms/neuroassist-ai/protocol/openid-connect/token");
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ---- register ----

    @Test
    @DisplayName("register: creates device and returns plaintext enrollment token")
    void registerCreatesDeviceAndToken() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge saved =
                new DispositivoEdge(
                        deviceId,
                        TENANT,
                        "My Device",
                        null,
                        PairingState.registered,
                        null,
                        null,
                        null,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        "op",
                        "op");
        when(deviceRepo.save(any())).thenReturn(saved);
        when(enrollmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceRegistrationUseCase.RegisterResult result = sut.register("My Device", null, "op");

        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.pairingState()).isEqualTo(PairingState.registered);
        assertThat(result.enrollmentToken()).startsWith("et_");
        assertThat(result.enrollmentExpiresAt()).isAfter(Instant.now());
        verify(enrollmentRepo).save(any(DispositivoEnrollment.class));
    }

    // ---- enroll ----

    @Test
    @DisplayName("enroll: valid token returns clientSecret once")
    void enrollValidTokenReturnsCredential() {
        UUID deviceId = UUID.randomUUID();
        String plainToken = "et_abc123";
        String tokenHash = DeviceRegistrationService.sha256Hex(plainToken);

        DispositivoEnrollment enrollment =
                DispositivoEnrollment.mint(
                        UUID.randomUUID(),
                        TENANT,
                        deviceId,
                        tokenHash,
                        Instant.now().plusSeconds(900));
        DispositivoEdge device =
                new DispositivoEdge(
                        deviceId,
                        TENANT,
                        "label",
                        null,
                        PairingState.registered,
                        null,
                        null,
                        null,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        "op",
                        "op");

        when(enrollmentRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(enrollment));
        when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(device));
        when(credentialRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceRegistrationUseCase.EnrollResult result = sut.enroll(plainToken, "1.4.2");

        assertThat(result.clientId()).isEqualTo("edge-" + deviceId);
        assertThat(result.clientSecret()).startsWith("cs_");
        assertThat(result.tokenScope()).isEqualTo("edge.device");
        assertThat(result.recommendedHeartbeatSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("enroll: expired token throws enrollment-invalid (401)")
    void enrollExpiredTokenThrows() {
        String tokenHash = DeviceRegistrationService.sha256Hex("et_expired");
        DispositivoEnrollment expired =
                DispositivoEnrollment.mint(
                        UUID.randomUUID(),
                        TENANT,
                        UUID.randomUUID(),
                        tokenHash,
                        Instant.now().minusSeconds(1));

        when(enrollmentRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> sut.enroll("et_expired", "1.0"))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.enrollment-invalid");
    }

    @Test
    @DisplayName("enroll: unknown token throws enrollment-invalid (does not reveal reason)")
    void enrollUnknownTokenThrows() {
        when(enrollmentRepo.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.enroll("et_unknown", "1.0"))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.enrollment-invalid");
    }

    @Test
    @DisplayName("enroll: already enrolled device throws already-enrolled (409)")
    void enrollAlreadyEnrolledDeviceThrows() {
        String tokenHash = DeviceRegistrationService.sha256Hex("et_dup");
        UUID deviceId = UUID.randomUUID();
        DispositivoEnrollment enrollment =
                DispositivoEnrollment.mint(
                        UUID.randomUUID(),
                        TENANT,
                        deviceId,
                        tokenHash,
                        Instant.now().plusSeconds(900));
        DispositivoEdge device =
                new DispositivoEdge(
                        deviceId,
                        TENANT,
                        "label",
                        UUID.randomUUID(),
                        PairingState.paired,
                        "1.0",
                        null,
                        null,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        "op",
                        "op");

        when(enrollmentRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(enrollment));
        when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> sut.enroll("et_dup", "1.0"))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.already-enrolled");
    }

    // ---- retire (EDGE-015, P4) ----

    @Test
    @DisplayName("retire: marks device retired and revokes active credential")
    void retireRevokesCredentialAndSetsRetired() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge device =
                new DispositivoEdge(
                        deviceId,
                        TENANT,
                        "label",
                        UUID.randomUUID(),
                        PairingState.paired,
                        "1.0",
                        Instant.now().minusSeconds(10),
                        1L,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        "op",
                        "op");

        DispositivoCredential activeCred =
                new DispositivoCredential(
                        UUID.randomUUID(),
                        TENANT,
                        deviceId,
                        "edge-" + deviceId,
                        "hash123",
                        CredentialStatus.active,
                        Instant.now().minusSeconds(60),
                        null);

        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(device));
        when(credentialRepo.findByDispositivoEdgeId(deviceId)).thenReturn(List.of(activeCred));
        when(credentialRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.retire(deviceId, "operator");

        verify(credentialRepo).save(any(DispositivoCredential.class));
        verify(transitionRepo).save(any(DispositivoEdgeTransition.class));
        verify(deviceRepo).save(any(DispositivoEdge.class));
    }

    @Test
    @DisplayName("retire: already retired device throws device-retired (409)")
    void retireAlreadyRetiredDeviceThrows() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge retired =
                new DispositivoEdge(
                        deviceId,
                        TENANT,
                        "label",
                        null,
                        PairingState.retired,
                        null,
                        null,
                        null,
                        0L,
                        Instant.now(),
                        Instant.now(),
                        "op",
                        "op");

        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> sut.retire(deviceId, "operator"))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.device-retired");
    }

    @Test
    @DisplayName("retire: device not found throws device-not-found (404)")
    void retireDeviceNotFoundThrows() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.retire(deviceId, "operator"))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.device-not-found");
    }
}
