package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.CaregiverRequest;
import com.neuroassist.acs.api.dto.CaregiverResponse;
import com.neuroassist.acs.application.port.in.CaregiverUseCase;
import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorPapel;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.infrastructure.security.TenantContextHolder;
import com.neuroassist.acs.shared.error.CaregiverNotFoundException;
import com.neuroassist.acs.shared.error.LastCaregiverLinkException;
import com.neuroassist.acs.shared.error.LoginDuplicateException;
import com.neuroassist.acs.shared.error.TenantMismatchException;
import com.neuroassist.acs.shared.error.TenantNotActiveException;
import com.neuroassist.acs.shared.error.TenantNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — CaregiverService. Use cases: create, findById, listByTenant, updateStatus.
 * RULE-03: @Transactional only here (application.service). SECURITY: BCrypt-hashes senha before
 * persisting; credentialHash never returned.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CaregiverService implements CaregiverUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CaregiverPatientLinkRepositoryPort linkRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public CaregiverResponse create(CaregiverRequest request, UUID tenantId) {
        // Check requested tenant exists and is ACTIVE (before JWT tenant match check)
        tenantRepository
                .findById(tenantId)
                .ifPresentOrElse(
                        t -> {
                            if (t.status() != TenantStatus.ACTIVE) {
                                throw new TenantNotActiveException(tenantId, t.status().name());
                            }
                        },
                        () -> {
                            throw new TenantNotFoundException(tenantId);
                        });

        // RULE-07: enforce tenant context; validate JWT tenant matches request tenantId
        String ctxTenantId = TenantContextHolder.require();
        if (!ctxTenantId.equals(tenantId.toString())) {
            throw new TenantMismatchException(tenantId, UUID.fromString(ctxTenantId));
        }

        // Check login uniqueness
        String login = request.login().toLowerCase();
        if (cuidadorRepository.findByLogin(login).isPresent()) {
            throw new LoginDuplicateException(login, tenantId);
        }

        log.info(
                "Creating caregiver: login={} tenantId={} papel={}",
                login,
                tenantId,
                request.papel());

        String credentialHash = passwordEncoder.encode(request.senha());

        Cuidador cuidador =
                new Cuidador(
                        null,
                        tenantId,
                        request.nome(),
                        login,
                        credentialHash,
                        CuidadorPapel.valueOf(request.papel()),
                        CuidadorStatus.ACTIVE,
                        Instant.now(),
                        null);

        Cuidador saved = cuidadorRepository.save(cuidador);
        log.info("Caregiver created: id={} tenantId={}", saved.id(), tenantId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CaregiverResponse findById(UUID cuidadorId) {
        // RULE-07: enforce tenant context — Hibernate multi-tenancy routes to correct schema
        TenantContextHolder.require();
        return cuidadorRepository
                .findById(cuidadorId)
                .map(this::toResponse)
                .orElseThrow(() -> new CaregiverNotFoundException(cuidadorId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaregiverResponse> listByTenant(UUID tenantId) {
        // RULE-07: enforce tenant context; validate JWT tenant matches request tenantId
        String ctxTenantId = TenantContextHolder.require();
        if (!ctxTenantId.equals(tenantId.toString())) {
            throw new TenantMismatchException(tenantId, UUID.fromString(ctxTenantId));
        }
        return cuidadorRepository.findByTenantId(tenantId).stream().map(this::toResponse).toList();
    }

    @Override
    public CaregiverResponse updateStatus(UUID cuidadorId, CuidadorStatus status) {
        // RULE-07: enforce tenant context — Hibernate multi-tenancy routes to correct schema
        TenantContextHolder.require();
        log.info("Updating caregiver status: cuidadorId={} newStatus={}", cuidadorId, status);

        Cuidador existing =
                cuidadorRepository
                        .findById(cuidadorId)
                        .orElseThrow(() -> new CaregiverNotFoundException(cuidadorId));

        // SAFETY-CRITICAL (D-204): block deactivation if caregiver is last active link for any patient
        if (status == CuidadorStatus.INACTIVE) {
            linkRepository.findActiveByCuidadorId(cuidadorId).forEach(link -> {
                long activeCount = linkRepository.countActiveByPacienteId(link.pacienteId());
                if (activeCount <= 1) {
                    log.error(
                            "SAFETY-CRITICAL: Caregiver {} is the last active link for patient {} (D-204)",
                            cuidadorId, link.pacienteId());
                    throw new LastCaregiverLinkException(cuidadorId);
                }
            });
        }

        Cuidador updated =
                new Cuidador(
                        existing.id(),
                        existing.tenantId(),
                        existing.nome(),
                        existing.login(),
                        existing.credentialHash(),
                        existing.papel(),
                        status,
                        existing.criadoEm(),
                        Instant.now());

        Cuidador saved = cuidadorRepository.save(updated);
        log.info("Caregiver status updated: id={} status={}", saved.id(), saved.status());
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Private mapping — SECURITY: credentialHash never included in response
    // -----------------------------------------------------------------------

    private CaregiverResponse toResponse(Cuidador c) {
        return new CaregiverResponse(
                c.id(),
                c.tenantId(),
                c.nome(),
                c.login(),
                c.papel().name(),
                c.status().name(),
                c.criadoEm());
    }
}
