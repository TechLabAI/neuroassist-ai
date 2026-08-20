package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.DirectoryResponse;
import com.neuroassist.acs.application.port.in.DirectoryUseCase;
import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.CaregiverPatientLink;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.infrastructure.security.TenantContextHolder;
import com.neuroassist.acs.shared.error.TenantNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — DirectoryService. Use case: resolveDirectory(tenantId, pacienteId). Returns
 * tenant validity + list of active responsible caregivers for a patient. Used by evnt-service,
 * dash-service, hist-service for authorization decisions. RULE-03: @Transactional only here
 * (application.service).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectoryService implements DirectoryUseCase {

    private final TenantRepositoryPort tenantRepository;
    private final CaregiverPatientLinkRepositoryPort linkRepository;
    private final CuidadorRepositoryPort cuidadorRepository;
    private final RoleAuthorityPolicy roleAuthorityPolicy;

    @Override
    public DirectoryResponse resolveDirectory(UUID tenantId, UUID pacienteId) {
        // RULE-07: enforce tenant context — Hibernate multi-tenancy routes to correct schema
        TenantContextHolder.require();
        log.debug("Resolving directory: tenantId={} pacienteId={}", tenantId, pacienteId);

        var tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));

        boolean tenantValid = tenant.status() == TenantStatus.ACTIVE;

        List<CaregiverPatientLink> activeLinks = linkRepository.findActiveByPacienteId(pacienteId);

        List<UUID> cuidadorIds =
                activeLinks.stream().map(CaregiverPatientLink::cuidadorId).toList();
        Map<UUID, Cuidador> cuidadorMap =
                cuidadorRepository.findAllByIds(cuidadorIds).stream()
                        .collect(Collectors.toMap(Cuidador::id, Function.identity()));

        List<DirectoryResponse.ResponsibleCaregiver> responsibleCaregivers =
                activeLinks.stream()
                        .map(link -> cuidadorMap.get(link.cuidadorId()))
                        .filter(c -> c != null)
                        .map(this::toResponsibleCaregiver)
                        .toList();

        log.debug(
                "Directory resolved: tenantId={} pacienteId={} tenantValid={} caregivers={}",
                tenantId,
                pacienteId,
                tenantValid,
                responsibleCaregivers.size());

        return new DirectoryResponse(
                tenantId,
                tenant.status().name(),
                tenantValid,
                tenant.tipo().name(),
                responsibleCaregivers);
    }

    @Override
    public DirectoryResponse.PatientCaregiversResponse getPatientCaregivers(UUID pacienteId) {
        // RULE-07: enforce tenant context
        TenantContextHolder.require();
        log.debug("Resolving caregivers for pacienteId={}", pacienteId);

        List<CaregiverPatientLink> activeLinks = linkRepository.findActiveByPacienteId(pacienteId);

        List<UUID> cuidadorIds =
                activeLinks.stream().map(CaregiverPatientLink::cuidadorId).toList();
        Map<UUID, Cuidador> cuidadorMap =
                cuidadorRepository.findAllByIds(cuidadorIds).stream()
                        .collect(Collectors.toMap(Cuidador::id, Function.identity()));

        List<DirectoryResponse.ResponsibleCaregiver> caregivers =
                activeLinks.stream()
                        .map(link -> cuidadorMap.get(link.cuidadorId()))
                        .filter(c -> c != null)
                        .map(this::toResponsibleCaregiver)
                        .toList();

        log.debug(
                "Patient caregivers resolved: pacienteId={} count={}",
                pacienteId,
                caregivers.size());
        return new DirectoryResponse.PatientCaregiversResponse(pacienteId, caregivers);
    }

    @Override
    public DirectoryResponse.TenantInfoResponse getTenantInfo(UUID tenantId) {
        // RULE-07: enforce tenant context
        TenantContextHolder.require();
        log.debug("Getting tenant info for tenantId={}", tenantId);

        var tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));

        return new DirectoryResponse.TenantInfoResponse(
                tenantId,
                tenant.status().name(),
                tenant.tipo().name(),
                tenant.status() == TenantStatus.ACTIVE);
    }

    @Override
    public DirectoryResponse.PatientScopeResponse getCaregiverPatientScope(UUID cuidadorId) {
        // RULE-07: enforce tenant context
        TenantContextHolder.require();
        log.debug("Resolving patient scope for cuidadorId={}", cuidadorId);

        List<CaregiverPatientLink> activeLinks = linkRepository.findActiveByCuidadorId(cuidadorId);

        List<UUID> patientIds = activeLinks.stream().map(CaregiverPatientLink::pacienteId).toList();

        log.debug(
                "Patient scope resolved: cuidadorId={} patients={}", cuidadorId, patientIds.size());
        return new DirectoryResponse.PatientScopeResponse(cuidadorId, patientIds);
    }

    // -----------------------------------------------------------------------
    // Private mapping
    // -----------------------------------------------------------------------

    private DirectoryResponse.ResponsibleCaregiver toResponsibleCaregiver(Cuidador cuidador) {
        List<String> authorities = roleAuthorityPolicy.toAuthorities(cuidador.papel());
        return new DirectoryResponse.ResponsibleCaregiver(
                cuidador.id(), cuidador.papel().name(), authorities);
    }
}
