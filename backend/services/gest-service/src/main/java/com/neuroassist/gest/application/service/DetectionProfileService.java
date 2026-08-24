package com.neuroassist.gest.application.service;

import com.neuroassist.gest.api.dto.DetectionProfilePrimitiveResponse;
import com.neuroassist.gest.api.dto.DetectionProfilePrimitiveResponse.Origem;
import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.EdgeDevicePort;
import com.neuroassist.gest.application.port.out.PatientCatalogPort;
import com.neuroassist.gest.application.port.out.TenantContextPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.event.DetectionProfilePublished;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.Sensibilidade;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.service.DetectionProfileDomainService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for DetectionProfile operations (GEST-009). Publishes versioned detection
 * profiles for the edge and exposes read operations. Tenant isolation is implicit via schema
 * routing set by TenantContextFilter.
 *
 * <p>Publish invariant (ADR-006): at least one primitive must be in SUPPORTED state. The published
 * profile is immutable after creation (only {@code active} flag can be flipped by a subsequent
 * publish).
 *
 * <p>NOTE — RULE-07: TenantContextHolder.require() is deferred to P5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DetectionProfileService implements DetectionProfileUseCase {

    /**
     * Subiu de v1.0.0 quando o perfil passou a carregar limiares tipados e a devolver os primitivos
     * (E2E-033). Menor, nao maior: o que existia continua no lugar e com o mesmo significado — o
     * agente antigo ignora os campos novos e segue funcionando.
     */
    private static final String CONTRACT_VERSION = "v1.1.0";

    private final DetectionProfileRepositoryPort detectionProfileRepositoryPort;
    private final DetectionProfilePrimitiveRepositoryPort detectionProfilePrimitiveRepositoryPort;
    private final TipoGestoRepositoryPort tipoGestoRepositoryPort;
    private final DetectionProfileDomainService detectionProfileDomainService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantContextPort tenantContextPort;
    private final EdgeDevicePort edgeDevicePort;
    private final PatientCatalogPort patientCatalogPort;

    /**
     * Publishes a new versioned DetectionProfile. Steps (GEST-009):
     *
     * <ol>
     *   <li>Load all SUPPORTED primitives — throw if none (invariant: empty-profile).
     *   <li>Increment monotonic version.
     *   <li>Deactivate any currently-active profile.
     *   <li>Persist the new profile and its primitive snapshot.
     *   <li>Emit {@link DetectionProfilePublished} in-process domain event.
     * </ol>
     */
    @Override
    public DetectionProfileResponse publish(DetectionProfileRequest request) {
        // 1. Load SUPPORTED primitives (tenant implicit via port schema routing)
        List<TipoGesto> supported =
                tipoGestoRepositoryPort.findAllByTenant().stream()
                        .filter(tg -> tg.detectabilityState() == DetectabilityState.SUPPORTED)
                        .toList();
        if (supported.isEmpty()) {
            throw new IllegalStateException("no supported primitives");
        }

        // 2. Next monotonic version + deactivate current (atomic from service perspective)
        long version = detectionProfileRepositoryPort.nextVersion();
        detectionProfileRepositoryPort.deactivateCurrent();

        // 3. Create and persist new DetectionProfile
        DetectionProfile profile =
                DetectionProfile.publish(
                        version,
                        CONTRACT_VERSION,
                        request.thresholds(),
                        supported,
                        tenantContextPort.getTenantIdOrNull(),
                        null);
        DetectionProfile saved = detectionProfileRepositoryPort.save(profile);

        // 4. Snapshot SUPPORTED primitives via domain service and persist
        List<DetectionProfilePrimitive> primitives =
                detectionProfileDomainService.createSnapshot(
                        supported, saved.id(), request.sensibilidadePorGesto());
        List<DetectionProfilePrimitive> savedPrimitives =
                detectionProfilePrimitiveRepositoryPort.saveAll(primitives);

        // 5. Publish in-process domain event (AFTER_COMMIT listener in P6)
        eventPublisher.publishEvent(
                new DetectionProfilePublished(saved.id(), saved.version(), Instant.now()));

        log.info(
                "DetectionProfile published: id={} version={} primitivos={}",
                saved.id(),
                saved.version(),
                savedPrimitives.size());
        return toResponse(saved, savedPrimitives);
    }

    /** Returns the currently active profile for the tenant, if any. */
    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionProfileResponse> findActive() {
        return detectionProfileRepositoryPort.findActive().map(this::toResponseWithPrimitives);
    }

    /**
     * O perfil de uma versão específica — inclusive uma já desativada.
     *
     * <p>Devolvia {@code Optional.empty()} sempre, então {@code GET
     * /detection-profile/&#123;version&#125;} era 404 para toda versão, inclusive as que existiam.
     * Isso tirava a única forma de auditar um relatório de gesto: o relatório cita a {@code
     * profileVersion} com que foi detectado, e sem esta leitura ninguém consegue dizer que limiares
     * estavam valendo naquele instante.
     *
     * <p>Os primitivos vêm junto pelo mesmo motivo do {@code /active} (E2E-033): um perfil sem
     * gesto nenhum não diz o que o agente estava detectando. Aqui eles saem como o perfil os
     * publicou — sem projeção de paciente, porque esta rota é da empresa e não conhece dispositivo.
     *
     * <p>O ETag é só {@code "<version>"}: uma versão publicada é imutável, então nada além dela
     * pode invalidar esta resposta. O ETag combinado com a versão do catálogo é exclusivo da
     * resposta por dispositivo, onde a recalibração do paciente muda o corpo sem mudar a versão do
     * perfil.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionProfileResponse> findByVersion(long version) {
        return detectionProfileRepositoryPort
                .findByVersion(version)
                .map(this::toResponseWithPrimitives);
    }

    /**
     * O perfil ativo projetado sobre a calibracao DO PACIENTE pareado com este dispositivo
     * (E2E-034).
     *
     * <p>Devolvia {@code Optional.empty()} sempre — 404 eterno —, e e exatamente o endpoint que o
     * EDGE chama no heartbeat: o ponteiro de perfil do EDGE leu "sem perfil ativo" para todo
     * dispositivo desde sempre.
     *
     * <p>A juncao acontece aqui, na leitura, e nao no publish: o perfil da empresa diz o que "alta"
     * significa; o catalogo do paciente diz qual faixa cada gesto dele usa. Nada e copiado para
     * dentro do perfil publicado, entao recalibrar um paciente nao exige republicar o perfil da
     * clinica — e nenhuma copia envelhece.
     *
     * <p>Nenhum dos dois lados externos derruba a resposta. Sem EDGE ou sem PAC, o dispositivo
     * recebe o perfil da empresa com a faixa padrao e a resposta diz que esta degradada: o aparelho
     * esta na casa do paciente, e detectar impreciso e melhor que nao detectar.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionProfileResponse> findForDevice(UUID deviceId) {
        Optional<DetectionProfile> active = detectionProfileRepositoryPort.findActive();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        DetectionProfile profile = active.get();
        List<DetectionProfilePrimitive> primitives =
                detectionProfilePrimitiveRepositoryPort.findByProfileId(profile.id());

        EdgeDevicePort.DeviceBinding binding = edgeDevicePort.resolveBinding(deviceId);
        if (!binding.hasPaciente()) {
            log.debug(
                    "Dispositivo {} sem paciente resolvido (degradado={}) — perfil da empresa",
                    deviceId,
                    binding.degraded());
            return Optional.of(toResponse(profile, primitives, null, null, binding.degraded()));
        }

        PatientCatalogPort.PatientBands bands = patientCatalogPort.bandsFor(binding.pacienteId());
        return Optional.of(toPatientResponse(profile, primitives, binding.pacienteId(), bands));
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    /**
     * Carrega os primitivos do perfil antes de responder.
     *
     * <p>Sem isto o agente de borda recebe um perfil sem gesto nenhum — os primitivos eram gravados
     * no publish e nunca lidos de volta, e o agente sempre-ligado abortava na partida (E2E-033).
     */
    private DetectionProfileResponse toResponseWithPrimitives(DetectionProfile profile) {
        return toResponse(
                profile, detectionProfilePrimitiveRepositoryPort.findByProfileId(profile.id()));
    }

    private DetectionProfileResponse toResponse(
            DetectionProfile profile, List<DetectionProfilePrimitive> primitives) {
        return toResponse(profile, primitives, null, null, false);
    }

    private DetectionProfileResponse toResponse(
            DetectionProfile profile,
            List<DetectionProfilePrimitive> primitives,
            UUID pacienteId,
            Integer catalogoVersao,
            boolean catalogoDegradado) {
        Origem origem = catalogoDegradado ? Origem.INDISPONIVEL : Origem.PERFIL_EMPRESA;
        return new DetectionProfileResponse(
                profile.id(),
                profile.version(),
                profile.active(),
                profile.contractVersion(),
                profile.thresholds(),
                primitives.stream()
                        .map(p -> DetectionProfilePrimitiveResponse.forPatient(p, null, origem))
                        .toList(),
                pacienteId,
                catalogoVersao,
                catalogoDegradado,
                profile.publishedAt(),
                etag(profile.version(), catalogoVersao));
    }

    /**
     * Projeta a calibracao do paciente sobre os primitivos do perfil da empresa.
     *
     * <p>Gesto com {@code comando} publicado recebe a faixa da cuidadora; gesto sem comando fica
     * com a do perfil e e marcado como tal. As duas coisas sao visiveis na resposta de proposito:
     * "media porque escolheram media" e "media porque ninguem calibrou" nao podem parecer iguais.
     */
    private DetectionProfileResponse toPatientResponse(
            DetectionProfile profile,
            List<DetectionProfilePrimitive> primitives,
            UUID pacienteId,
            PatientCatalogPort.PatientBands bands) {
        Map<UUID, Sensibilidade> porPrimitivo = bands.porPrimitivo();
        Origem semComando = bands.degraded() ? Origem.INDISPONIVEL : Origem.PERFIL_EMPRESA;

        List<DetectionProfilePrimitiveResponse> projetados =
                primitives.stream()
                        .map(
                                p -> {
                                    Sensibilidade doPaciente = porPrimitivo.get(p.tipoGestoId());
                                    return DetectionProfilePrimitiveResponse.forPatient(
                                            p,
                                            doPaciente,
                                            doPaciente != null ? Origem.PACIENTE : semComando);
                                })
                        .toList();

        log.debug(
                "Perfil v{} projetado para o paciente {} — {}/{} calibrado(s), catalogo v{}",
                profile.version(),
                pacienteId,
                porPrimitivo.size(),
                projetados.size(),
                bands.catalogoVersao());

        return new DetectionProfileResponse(
                profile.id(),
                profile.version(),
                profile.active(),
                profile.contractVersion(),
                profile.thresholds(),
                projetados,
                pacienteId,
                bands.catalogoVersao(),
                bands.degraded(),
                profile.publishedAt(),
                etag(profile.version(), bands.catalogoVersao()));
    }

    /**
     * ETag da resposta por dispositivo combina as DUAS versoes.
     *
     * <p>So {@code profileVersion} faria a recalibracao de um paciente nao invalidar o cache do
     * dispositivo dele: a cuidadora mudaria de {@code media} para {@code alta}, o GEST responderia
     * {@code 304}, e o aparelho seguiria detectando com a faixa velha por tempo indeterminado.
     */
    private String etag(long profileVersion, Integer catalogoVersao) {
        return catalogoVersao == null
                ? "\"" + profileVersion + "\""
                : "\"" + profileVersion + "-" + catalogoVersao + "\"";
    }
}
