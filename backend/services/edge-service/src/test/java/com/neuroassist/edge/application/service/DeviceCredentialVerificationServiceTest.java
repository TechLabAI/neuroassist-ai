package com.neuroassist.edge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neuroassist.edge.application.port.out.DeviceDirectoryPort;
import com.neuroassist.edge.application.port.out.DispositivoCredentialRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.domain.model.CredentialStatus;
import com.neuroassist.edge.domain.model.DispositivoCredential;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
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
 * C-18 — verificação de credencial de dispositivo (service-identity.md §7).
 *
 * <p>O cenário que importa é o de <b>sem tenant vinculado</b>: o ACS chama com identidade de
 * serviço e o {@code TenantContextHolder} pode estar vazio. Cada teste começa com o holder limpo e
 * afirma que ele volta a ficar limpo no fim — provando que a resolução veio do índice global e que
 * não há vazamento de ThreadLocal.
 */
@ExtendWith(MockitoExtension.class)
class DeviceCredentialVerificationServiceTest {

    @Mock private DeviceDirectoryPort deviceDirectory;
    @Mock private DispositivoCredentialRepositoryPort credentialRepo;
    @Mock private DispositivoEdgeRepositoryPort deviceRepo;

    private final DispositivoEdgeDomainService domainService = new DispositivoEdgeDomainService();
    private DeviceCredentialVerificationService sut;

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final UUID DEVICE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String CLIENT_ID = "edge-" + DEVICE_ID;
    private static final String SECRET = "cs_deadbeef";

    @BeforeEach
    void setUp() {
        sut =
                new DeviceCredentialVerificationService(
                        deviceDirectory, credentialRepo, deviceRepo, domainService);
        // Sem tenant vinculado — é assim que a requisição do ACS chega.
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ---- caminho feliz ----

    @Test
    @DisplayName("verify: sem tenant vinculado, resolve pelo índice global e valida o segredo")
    void verify_semTenantVinculado_resolvePeloIndiceGlobal() {
        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();

        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        when(credentialRepo.findActiveByClientId(CLIENT_ID))
                .thenReturn(Optional.of(credential(sha256Hex(SECRET))));
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT))
                .thenReturn(Optional.of(device(PairingState.paired)));

        var result = sut.verify(CLIENT_ID, SECRET);

        assertThat(result.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(result.tenantId()).isEqualTo(TENANT);
        // O tenant foi vinculado durante a operação e restaurado depois (não vaza no pool).
        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("verify: o tenant do índice global está vinculado quando a credencial é buscada")
    void verify_vinculaTenantAntesDaConsultaNoSchemaDoTenant() {
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        // A consulta a dispositivo_credential só encontra a linha se o search_path já apontar
        // para o schema do tenant — aqui isso é observado pelo holder no momento da chamada.
        when(credentialRepo.findActiveByClientId(CLIENT_ID))
                .thenAnswer(
                        inv -> {
                            assertThat(TenantContextHolder.require()).isEqualTo(TENANT);
                            return Optional.of(credential(sha256Hex(SECRET)));
                        });
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT))
                .thenReturn(Optional.of(device(PairingState.paired)));

        sut.verify(CLIENT_ID, SECRET);
    }

    @Test
    @DisplayName("verify: tenant pré-existente do chamador é restaurado ao final")
    void verify_restauraTenantAnterior() {
        TenantContextHolder.set("tenant-do-chamador");
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        when(credentialRepo.findActiveByClientId(CLIENT_ID))
                .thenReturn(Optional.of(credential(sha256Hex(SECRET))));
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT))
                .thenReturn(Optional.of(device(PairingState.paired)));

        sut.verify(CLIENT_ID, SECRET);

        assertThat(TenantContextHolder.getTenantIdOrNull()).isEqualTo("tenant-do-chamador");
    }

    // ---- 401 genérico: todos os motivos são indistinguíveis ----

    @Test
    @DisplayName("verify: clientId fora do índice global → edge.credential-invalid")
    void verify_clientIdDesconhecido() {
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.verify(CLIENT_ID, SECRET))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");

        // Não chega a tocar o schema do tenant.
        verifyNoInteractions(credentialRepo, deviceRepo);
        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("verify: sem credencial ativa (revogada) → mesmo edge.credential-invalid")
    void verify_credencialRevogada() {
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        when(credentialRepo.findActiveByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.verify(CLIENT_ID, SECRET))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");
        verify(deviceRepo, never()).findByIdAndTenantId(any(), anyString());
    }

    @Test
    @DisplayName("verify: segredo errado → mesmo edge.credential-invalid")
    void verify_segredoErrado() {
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        when(credentialRepo.findActiveByClientId(CLIENT_ID))
                .thenReturn(Optional.of(credential(sha256Hex("cs_outro"))));

        assertThatThrownBy(() -> sut.verify(CLIENT_ID, SECRET))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");
        verify(deviceRepo, never()).findByIdAndTenantId(any(), anyString());
    }

    @Test
    @DisplayName("verify: dispositivo aposentado → mesmo edge.credential-invalid")
    void verify_dispositivoAposentado() {
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        when(credentialRepo.findActiveByClientId(CLIENT_ID))
                .thenReturn(Optional.of(credential(sha256Hex(SECRET))));
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT))
                .thenReturn(Optional.of(device(PairingState.retired)));

        assertThatThrownBy(() -> sut.verify(CLIENT_ID, SECRET))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");
        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("verify: dispositivo ausente no tenant → mesmo edge.credential-invalid")
    void verify_dispositivoAusente() {
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        when(credentialRepo.findActiveByClientId(CLIENT_ID))
                .thenReturn(Optional.of(credential(sha256Hex(SECRET))));
        when(deviceRepo.findByIdAndTenantId(DEVICE_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.verify(CLIENT_ID, SECRET))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");
    }

    @Test
    @DisplayName("verify: ponteiro global apontando para tenant divergente → rejeita")
    void verify_tenantDivergente() {
        when(deviceDirectory.resolveTenant(CLIENT_ID)).thenReturn(Optional.of(TENANT));
        DispositivoCredential outroTenant =
                new DispositivoCredential(
                        UUID.randomUUID(),
                        "outro-tenant",
                        DEVICE_ID,
                        CLIENT_ID,
                        sha256Hex(SECRET),
                        CredentialStatus.active,
                        Instant.now(),
                        null);
        when(credentialRepo.findActiveByClientId(CLIENT_ID)).thenReturn(Optional.of(outroTenant));

        assertThatThrownBy(() -> sut.verify(CLIENT_ID, SECRET))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");
    }

    @Test
    @DisplayName("verify: clientId ou clientSecret em branco → rejeita sem consultar nada")
    void verify_entradaEmBranco() {
        assertThatThrownBy(() -> sut.verify("  ", SECRET))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");
        assertThatThrownBy(() -> sut.verify(CLIENT_ID, ""))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");
        assertThatThrownBy(() -> sut.verify(null, null))
                .isInstanceOf(CodedException.class)
                .hasFieldOrPropertyWithValue("code", "edge.credential-invalid");

        verifyNoInteractions(deviceDirectory, credentialRepo, deviceRepo);
    }

    // ---- helpers ----

    private static DispositivoCredential credential(String secretHash) {
        return new DispositivoCredential(
                UUID.randomUUID(),
                TENANT,
                DEVICE_ID,
                CLIENT_ID,
                secretHash,
                CredentialStatus.active,
                Instant.now(),
                null);
    }

    private static DispositivoEdge device(PairingState state) {
        Instant now = Instant.now();
        return new DispositivoEdge(
                DEVICE_ID,
                TENANT,
                "device-1",
                state == PairingState.paired ? UUID.randomUUID() : null,
                state,
                "1.0.0",
                now,
                1L,
                0L,
                now,
                now,
                "system",
                "system");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
