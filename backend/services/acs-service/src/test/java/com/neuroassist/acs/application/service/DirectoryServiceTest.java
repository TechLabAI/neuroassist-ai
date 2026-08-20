package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.DirectoryResponse;
import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.CaregiverPatientLink;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorPapel;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.domain.model.LinkStatus;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import com.neuroassist.acs.infrastructure.security.TenantContextHolder;
import com.neuroassist.acs.shared.error.TenantNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DirectoryService (ACS-011, P3). Validates directory resolution: tenant validity +
 * responsible caregivers.
 */
@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

    @Mock private TenantRepositoryPort tenantRepository;

    @Mock private CaregiverPatientLinkRepositoryPort linkRepository;

    @Mock private CuidadorRepositoryPort cuidadorRepository;

    private RoleAuthorityPolicy roleAuthorityPolicy;
    private DirectoryService directoryService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID pacienteId = UUID.randomUUID();
    private final UUID cuidadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        roleAuthorityPolicy = new RoleAuthorityPolicy();
        directoryService =
                new DirectoryService(
                        tenantRepository, linkRepository, cuidadorRepository, roleAuthorityPolicy);
        // RULE-07: populate TenantContextHolder as TenantContextFilter would in real requests
        TenantContextHolder.set(tenantId.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void resolveDirectory_activeTenantWithCaregivers_returnsFullDirectory() {
        Tenant tenant =
                new Tenant(
                        tenantId,
                        "Clínica A",
                        TenantTipo.CLINICA,
                        TenantStatus.ACTIVE,
                        "t_abc123",
                        Instant.now());

        Cuidador cuidador =
                new Cuidador(
                        cuidadorId,
                        tenantId,
                        "Maria",
                        "maria",
                        "$hash",
                        CuidadorPapel.CUIDADORA,
                        CuidadorStatus.ACTIVE,
                        Instant.now(),
                        null);

        CaregiverPatientLink link =
                new CaregiverPatientLink(
                        UUID.randomUUID(),
                        tenantId,
                        cuidadorId,
                        pacienteId,
                        LinkStatus.ACTIVE,
                        Instant.now(),
                        null);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(linkRepository.findActiveByPacienteId(pacienteId)).thenReturn(List.of(link));
        when(cuidadorRepository.findAllByIds(List.of(cuidadorId))).thenReturn(List.of(cuidador));

        DirectoryResponse response = directoryService.resolveDirectory(tenantId, pacienteId);

        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.tenantStatus()).isEqualTo("ACTIVE");
        assertThat(response.tenantValid()).isTrue();
        assertThat(response.tenantTipo()).isEqualTo("CLINICA");
        assertThat(response.responsibleCaregivers()).hasSize(1);
        assertThat(response.responsibleCaregivers().get(0).cuidadorId()).isEqualTo(cuidadorId);
        assertThat(response.responsibleCaregivers().get(0).papel()).isEqualTo("CUIDADORA");
        // CUIDADORA authorities = 5 items
        assertThat(response.responsibleCaregivers().get(0).authorities()).hasSize(5);
    }

    @Test
    void resolveDirectory_suspendedTenant_returnsTenantValidFalse() {
        Tenant tenant =
                new Tenant(
                        tenantId,
                        "Clínica B",
                        TenantTipo.CLINICA,
                        TenantStatus.SUSPENDED,
                        "t_def456",
                        Instant.now());

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(linkRepository.findActiveByPacienteId(pacienteId)).thenReturn(List.of());

        DirectoryResponse response = directoryService.resolveDirectory(tenantId, pacienteId);

        assertThat(response.tenantValid()).isFalse();
        assertThat(response.tenantStatus()).isEqualTo("SUSPENDED");
        assertThat(response.responsibleCaregivers()).isEmpty();
    }

    @Test
    void resolveDirectory_patientWithNoActiveLinks_returnsEmptyCaregivers() {
        Tenant tenant =
                new Tenant(
                        tenantId,
                        "Clínica C",
                        TenantTipo.CLINICA,
                        TenantStatus.ACTIVE,
                        "t_ghi789",
                        Instant.now());

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(linkRepository.findActiveByPacienteId(pacienteId)).thenReturn(List.of());

        DirectoryResponse response = directoryService.resolveDirectory(tenantId, pacienteId);

        assertThat(response.tenantValid()).isTrue();
        assertThat(response.responsibleCaregivers()).isEmpty();
    }

    @Test
    void resolveDirectory_tenantNotFound_throwsTenantNotFoundException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directoryService.resolveDirectory(tenantId, pacienteId))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
