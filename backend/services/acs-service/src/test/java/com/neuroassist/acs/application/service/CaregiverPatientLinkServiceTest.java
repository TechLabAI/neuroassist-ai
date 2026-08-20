package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.CaregiverPatientLinkResponse;
import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.PacPatientResolvePort;
import com.neuroassist.acs.domain.model.CaregiverPatientLink;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorPapel;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.domain.model.LinkStatus;
import com.neuroassist.acs.infrastructure.security.TenantContextHolder;
import com.neuroassist.acs.shared.error.CaregiverNotFoundException;
import com.neuroassist.acs.shared.error.LastCaregiverLinkException;
import com.neuroassist.acs.shared.error.TenantMismatchException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for CaregiverPatientLinkService (ACS-015, P3). SAFETY-CRITICAL: validates coverage
 * invariant (D-204) — last link cannot be revoked.
 */
@ExtendWith(MockitoExtension.class)
class CaregiverPatientLinkServiceTest {

    @Mock private CaregiverPatientLinkRepositoryPort linkRepository;
    @Mock private CuidadorRepositoryPort cuidadorRepository;
    @Mock private PacPatientResolvePort pacPatientResolvePort;

    private CaregiverPatientLinkService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID cuidadorId = UUID.randomUUID();
    private final UUID pacienteId = UUID.randomUUID();
    private final UUID linkId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CaregiverPatientLinkService(linkRepository, cuidadorRepository, pacPatientResolvePort);
        // RULE-07: populate TenantContextHolder as TenantContextFilter would in real requests
        TenantContextHolder.set(tenantId.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private Cuidador makeCuidador(UUID id, UUID tenant) {
        return new Cuidador(
                id,
                tenant,
                "Nome",
                "login",
                "$hash",
                CuidadorPapel.CUIDADORA,
                CuidadorStatus.ACTIVE,
                Instant.now(),
                null);
    }

    private CaregiverPatientLink makeActiveLink(UUID id) {
        return new CaregiverPatientLink(
                id, tenantId, cuidadorId, pacienteId, LinkStatus.ACTIVE, Instant.now(), null);
    }

    // -----------------------------------------------------------------------
    // createLink
    // -----------------------------------------------------------------------

    @Test
    void createLink_validInputs_returnsCreatedLink() {
        Cuidador cuidador = makeCuidador(cuidadorId, tenantId);
        CaregiverPatientLink saved = makeActiveLink(linkId);

        // No existing active link (idempotency)
        when(linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId))
                .thenReturn(Optional.empty());
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(cuidador));
        // PAC resolves patient in same tenant
        when(pacPatientResolvePort.resolvePatient(pacienteId, tenantId))
                .thenReturn(Optional.of(new PacPatientResolvePort.PatientResolveResponse(pacienteId, tenantId, "Paciente Teste", "ACTIVE")));
        when(linkRepository.save(any())).thenReturn(saved);

        CaregiverPatientLinkResponse response =
                service.createLink(cuidadorId, pacienteId, tenantId);

        assertThat(response.cuidadorId()).isEqualTo(cuidadorId);
        assertThat(response.pacienteId()).isEqualTo(pacienteId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.revogadoEm()).isNull();
    }

    @Test
    void createLink_caregiverNotFound_throwsCaregiverNotFoundException() {
        when(linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId))
                .thenReturn(Optional.empty());
        // PAC must resolve patient first (service calls PAC before caregiver check)
        when(pacPatientResolvePort.resolvePatient(pacienteId, tenantId))
                .thenReturn(Optional.of(new PacPatientResolvePort.PatientResolveResponse(
                        pacienteId, tenantId, "Paciente", "ACTIVE")));
        when(cuidadorRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createLink(cuidadorId, pacienteId, tenantId))
                .isInstanceOf(CaregiverNotFoundException.class);
    }

    @Test
    void createLink_caregiverBelongsToDifferentTenant_throwsTenantMismatchException() {
        UUID otherTenant = UUID.randomUUID();
        Cuidador cuidador = makeCuidador(cuidadorId, otherTenant);

        when(linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId))
                .thenReturn(Optional.empty());
        // PAC must resolve patient first (service calls PAC before caregiver check)
        when(pacPatientResolvePort.resolvePatient(pacienteId, tenantId))
                .thenReturn(Optional.of(new PacPatientResolvePort.PatientResolveResponse(
                        pacienteId, tenantId, "Paciente", "ACTIVE")));
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(cuidador));

        assertThatThrownBy(() -> service.createLink(cuidadorId, pacienteId, tenantId))
                .isInstanceOf(TenantMismatchException.class);
    }

    // -----------------------------------------------------------------------
    // revokeLink — SAFETY-CRITICAL tests
    // -----------------------------------------------------------------------

    @Test
    void revokeLink_withMultipleActiveLinks_revokesSuccessfully() {
        CaregiverPatientLink activeLink = makeActiveLink(linkId);
        CaregiverPatientLink revokedLink =
                new CaregiverPatientLink(
                        linkId,
                        tenantId,
                        cuidadorId,
                        pacienteId,
                        LinkStatus.REVOKED,
                        activeLink.criadoEm(),
                        Instant.now());

        when(linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId))
                .thenReturn(Optional.of(activeLink));
        when(linkRepository.countActiveByPacienteId(pacienteId)).thenReturn(2L);
        when(linkRepository.save(any())).thenReturn(revokedLink);

        CaregiverPatientLinkResponse response = service.revokeLink(cuidadorId, pacienteId);

        assertThat(response.status()).isEqualTo("REVOKED");
        assertThat(response.revogadoEm()).isNotNull();
    }

    @Test
    void revokeLink_lastActiveLinkForPatient_throwsLastCaregiverLinkException() {
        // SAFETY-CRITICAL (D-204): must block revocation of last active link
        CaregiverPatientLink activeLink = makeActiveLink(linkId);

        when(linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId))
                .thenReturn(Optional.of(activeLink));
        when(linkRepository.countActiveByPacienteId(pacienteId)).thenReturn(1L);

        assertThatThrownBy(() -> service.revokeLink(cuidadorId, pacienteId))
                .isInstanceOf(LastCaregiverLinkException.class)
                .hasMessageContaining(pacienteId.toString());
    }

    @Test
    void revokeLink_linkNotFound_returnsPhantomRevokedLink() {
        // Idempotent: no active link, no revoked link → return phantom revoked response (200)
        when(linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId))
                .thenReturn(Optional.empty());
        when(linkRepository.findByCuidadorIdAndPacienteId(cuidadorId, pacienteId))
                .thenReturn(Optional.empty());

        CaregiverPatientLinkResponse response = service.revokeLink(cuidadorId, pacienteId);

        assertThat(response.status()).isEqualTo("REVOKED");
    }
}
