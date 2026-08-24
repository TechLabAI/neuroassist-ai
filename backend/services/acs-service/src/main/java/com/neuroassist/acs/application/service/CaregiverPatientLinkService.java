package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.CaregiverPatientLinkResponse;
import com.neuroassist.acs.application.port.in.CaregiverPatientLinkUseCase;
import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.PacPatientResolvePort;
import com.neuroassist.acs.domain.model.CaregiverPatientLink;
import com.neuroassist.acs.domain.model.LinkStatus;
import com.neuroassist.acs.infrastructure.security.TenantContextHolder;
import com.neuroassist.acs.shared.error.CaregiverNotFoundException;
import com.neuroassist.acs.shared.error.CrossTenantLinkException;
import com.neuroassist.acs.shared.error.LastCaregiverLinkException;
import com.neuroassist.acs.shared.error.TenantMismatchException;
import com.neuroassist.acs.shared.error.UnknownPatientException;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — CaregiverPatientLinkService. Use cases: createLink, revokeLink.
 * RULE-03: @Transactional only here (application.service). SAFETY-CRITICAL (D-204): enforces
 * coverage invariant — a patient must always have at least one active caregiver. Revoking the last
 * link throws LastCaregiverLinkException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CaregiverPatientLinkService implements CaregiverPatientLinkUseCase {

    private final CaregiverPatientLinkRepositoryPort linkRepository;
    private final CuidadorRepositoryPort cuidadorRepository;
    private final PacPatientResolvePort pacPatientResolvePort;

    @Override
    public CaregiverPatientLinkResponse createLink(
            UUID cuidadorId, UUID pacienteId, UUID tenantId) {
        // RULE-07: enforce tenant context; validate JWT tenant matches request tenantId
        String ctxTenantId = TenantContextHolder.require();
        if (!ctxTenantId.equals(tenantId.toString())) {
            throw new TenantMismatchException(tenantId, UUID.fromString(ctxTenantId));
        }

        // Idempotency: return existing active link if it already exists (handled by controller for
        // proper 200 vs 201, but kept here as defence-in-depth)
        Optional<CaregiverPatientLink> existing =
                linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId);
        if (existing.isPresent()) {
            log.debug(
                    "Idempotent: active link already exists for cuidadorId={} pacienteId={}",
                    cuidadorId,
                    pacienteId);
            return toResponse(existing.get());
        }

        log.info(
                "Creating caregiver-patient link: cuidadorId={} pacienteId={} tenantId={}",
                cuidadorId,
                pacienteId,
                tenantId);

        // Verify patient exists via PAC service FIRST (so unknown/unreachable PAC returns 422/502
        // regardless of whether the caregiver ID is known)
        PacPatientResolvePort.PatientResolveResponse patient;
        try {
            patient =
                    pacPatientResolvePort
                            .resolvePatient(pacienteId, tenantId)
                            .orElseThrow(() -> new UnknownPatientException(pacienteId));
        } catch (UnknownPatientException e) {
            throw e;
        } catch (Exception e) {
            log.error("PAC unavailable when resolving patient={}: {}", pacienteId, e.getMessage());
            throw new UpstreamUnavailableException("pac-service", e);
        }

        if (!tenantId.equals(patient.tenantId())) {
            throw new CrossTenantLinkException(tenantId, patient.tenantId());
        }

        // Verify caregiver exists and belongs to the same tenant
        var cuidador =
                cuidadorRepository
                        .findById(cuidadorId)
                        .orElseThrow(() -> new CaregiverNotFoundException(cuidadorId));

        if (!cuidador.tenantId().equals(tenantId)) {
            throw new TenantMismatchException(tenantId, cuidador.tenantId());
        }

        CaregiverPatientLink link =
                new CaregiverPatientLink(
                        null,
                        tenantId,
                        cuidadorId,
                        pacienteId,
                        LinkStatus.ACTIVE,
                        Instant.now(),
                        null);

        CaregiverPatientLink saved = linkRepository.save(link);
        log.info(
                "Caregiver-patient link created: id={} cuidadorId={} pacienteId={}",
                saved.id(),
                cuidadorId,
                pacienteId);
        return toResponse(saved);
    }

    @Override
    public CaregiverPatientLinkResponse revokeLink(UUID cuidadorId, UUID pacienteId) {
        // RULE-07: enforce tenant context — Hibernate multi-tenancy routes to correct schema
        TenantContextHolder.require();
        log.info(
                "Revoking caregiver-patient link: cuidadorId={} pacienteId={}",
                cuidadorId,
                pacienteId);

        // Idempotency: if no active link, check for already-revoked link → return it (200)
        Optional<CaregiverPatientLink> activeLink =
                linkRepository.findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId);

        if (activeLink.isEmpty()) {
            Optional<CaregiverPatientLink> revokedLink =
                    linkRepository.findByCuidadorIdAndPacienteId(cuidadorId, pacienteId);
            if (revokedLink.isPresent()) {
                log.debug(
                        "Idempotent: link already revoked for cuidadorId={} pacienteId={}",
                        cuidadorId,
                        pacienteId);
                return toResponse(revokedLink.get());
            }
            log.warn(
                    "No link found for revocation: cuidadorId={} pacienteId={} — treating as idempotent",
                    cuidadorId,
                    pacienteId);
            CaregiverPatientLink phantom =
                    new CaregiverPatientLink(
                            null,
                            null,
                            cuidadorId,
                            pacienteId,
                            LinkStatus.REVOKED,
                            Instant.now(),
                            Instant.now());
            return toResponse(phantom);
        }

        CaregiverPatientLink link = activeLink.get();

        // SAFETY-CRITICAL (D-204): enforce coverage invariant
        long activeLinksCount = linkRepository.countActiveByPacienteId(pacienteId);
        if (activeLinksCount <= 1) {
            log.error(
                    "SAFETY-CRITICAL: Attempt to revoke last active caregiver link "
                            + "for pacienteId={} (D-204 coverage invariant violation)",
                    pacienteId);
            throw new LastCaregiverLinkException(pacienteId);
        }

        // Revoke the link (status transition only — no physical delete)
        CaregiverPatientLink revoked =
                new CaregiverPatientLink(
                        link.id(),
                        link.tenantId(),
                        link.cuidadorId(),
                        link.pacienteId(),
                        LinkStatus.REVOKED,
                        link.criadoEm(),
                        Instant.now());

        CaregiverPatientLink saved = linkRepository.save(revoked);
        log.info(
                "Caregiver-patient link revoked: id={} cuidadorId={} pacienteId={}",
                saved.id(),
                cuidadorId,
                pacienteId);
        return toResponse(saved);
    }

    /**
     * Revoga todos os vinculos ativos de um paciente apagado (LGPD).
     *
     * <p><b>Nao chama {@code countActiveByPacienteId}, e essa ausencia e o ponto.</b> A invariante
     * D-204 garante que um paciente sempre tenha alguem para quem seu alarme va; um paciente cujos
     * dados foram apagados nao produz mais alarme. Aplicar a invariante aqui protegeria um caminho
     * que nao existe mais, ao preco de manter viva a associacao "esta cuidadora cuida desta
     * pessoa" — justamente o que o apagamento se propos a remover.
     *
     * <p>Que isto seja um metodo separado, com authority separada, e deliberado: e a unica porta
     * do sistema que atravessa uma invariante SAFETY-CRITICAL, e ela tem de ser visivel como tal.
     *
     * <p>Idempotente: sem vinculos ativos, devolve zero.
     */
    @Override
    @Transactional
    public int revokeAllForErasedPatient(UUID pacienteId) {
        TenantContextHolder.require(); // RULE-07

        List<CaregiverPatientLink> ativos = linkRepository.findActiveByPacienteId(pacienteId);
        if (ativos.isEmpty()) {
            log.info("Paciente {} apagado nao tinha vinculos ativos — nada a revogar", pacienteId);
            return 0;
        }

        Instant agora = Instant.now();
        for (CaregiverPatientLink link : ativos) {
            linkRepository.save(
                    new CaregiverPatientLink(
                            link.id(),
                            link.tenantId(),
                            link.cuidadorId(),
                            link.pacienteId(),
                            LinkStatus.REVOKED,
                            link.criadoEm(),
                            agora));
        }

        // ERROR e nao INFO: atravessar D-204 e raro e irreversivel, e quem investigar um incidente
        // depois precisa achar esta linha sem procurar.
        log.error(
                "LGPD: {} vinculo(s) revogado(s) para o paciente apagado {} — invariante D-204"
                        + " deliberadamente atravessada (paciente apagado nao produz alarme)",
                ativos.size(),
                pacienteId);
        return ativos.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaregiverPatientLinkResponse> findActiveLink(UUID cuidadorId, UUID pacienteId) {
        TenantContextHolder.require();
        return linkRepository
                .findActiveByCuidadorIdAndPacienteId(cuidadorId, pacienteId)
                .map(this::toResponse);
    }

    // -----------------------------------------------------------------------
    // Private mapping
    // -----------------------------------------------------------------------

    private CaregiverPatientLinkResponse toResponse(CaregiverPatientLink link) {
        return new CaregiverPatientLinkResponse(
                link.id(),
                link.tenantId(),
                link.cuidadorId(),
                link.pacienteId(),
                link.status().name(),
                link.criadoEm(),
                link.revogadoEm());
    }
}
