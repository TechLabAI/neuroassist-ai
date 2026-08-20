package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.api.dto.ComandoAuthoringRequest;
import com.neuroassist.pac.api.dto.ComandoAuthoringResponse;
import com.neuroassist.pac.application.port.in.ComandoAuthoringUseCase;
import com.neuroassist.pac.application.port.in.IdempotencyUseCase;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.shared.audit.AuditAction;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAC-011 — ComandoController: REST surface for Comando authoring (draft lifecycle).
 *
 * <p>Path: {@code /api/pac/v1/pacientes/{pacienteId}/comandos}. Covers POST (create draft), GET
 * (list all), GET /{id} (single), PUT /{id} (edit draft).
 *
 * <p>Architectural rules:
 *
 * <ul>
 *   <li>RULE-04: no infrastructure.* imports.
 *   <li>RULE-03: no @Transactional — boundary is in ComandoAuthoringService.
 *   <li>Never returns JPA @Entity — always ComandoAuthoringResponse DTO records.
 * </ul>
 *
 * <p>ETag format: RFC 7232 strong ETag — e.g. {@code "1"} (quoted integer = rowVersion + 1), parsed
 * from the {@code If-Match} header on PUT operations (optimistic locking).
 */
@Slf4j
@RestController
@RequestMapping("/api/pac/v1/pacientes/{pacienteId}/comandos")
@RequiredArgsConstructor
public class ComandoAuthoringController {

    private static final String RESOURCE_TYPE_COMANDO = "comando";

    private final ComandoAuthoringUseCase comandoAuthoringUseCase;
    private final IdempotencyUseCase idempotencyUseCase;

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes/{pacienteId}/comandos → 201 Created
    // -------------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAuthority('pac.comando-authoring.criar')")
    @AuditAction("CREATE_COMANDO_DRAFT")
    public ResponseEntity<ComandoAuthoringResponse> createDraft(
            @PathVariable UUID pacienteId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ComandoAuthoringRequest request) {

        // PAC-023 — Idempotency-Key deduplication (SPEC-05 §3)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String bodyHash = hashComandoRequest(pacienteId, request);
            Optional<UUID> existing =
                    idempotencyUseCase.checkKey(idempotencyKey, RESOURCE_TYPE_COMANDO, bodyHash);
            if (existing.isPresent()) {
                // Replay: return 200 with original resource (SPEC-05 §3.2 — same key + same body)
                log.debug(
                        "POST /pacientes/{}/comandos idempotency hit key={} → 200",
                        pacienteId,
                        idempotencyKey);
                Comando original =
                        comandoAuthoringUseCase.findByIdAndPacienteId(pacienteId, existing.get());
                return ResponseEntity.ok(ComandoAuthoringResponse.from(original));
            }

            // New key: proceed with creation, then register
            Comando result =
                    comandoAuthoringUseCase.createDraft(
                            pacienteId,
                            request.rotulo(),
                            request.necessidade(),
                            request.primitivoPrincipalId(),
                            request.primitivoSecundarioId(),
                            request.sensibilidade(),
                            request.tempoMinGestoMs(),
                            request.tempoMaxGestoMs(),
                            request.urgencia());
            idempotencyUseCase.registerKey(
                    idempotencyKey, RESOURCE_TYPE_COMANDO, result.id(), bodyHash);

            ComandoAuthoringResponse response = ComandoAuthoringResponse.from(result);
            URI location =
                    URI.create(
                            "/api/pac/v1/pacientes/" + pacienteId + "/comandos/" + response.id());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(location);
            headers.setETag(response.etag());
            log.debug(
                    "POST /pacientes/{}/comandos → 201 id={} etag={} idempotencyKey={}",
                    pacienteId,
                    response.id(),
                    response.etag(),
                    idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(response);
        }

        // No Idempotency-Key header: standard creation
        Comando result =
                comandoAuthoringUseCase.createDraft(
                        pacienteId,
                        request.rotulo(),
                        request.necessidade(),
                        request.primitivoPrincipalId(),
                        request.primitivoSecundarioId(),
                        request.sensibilidade(),
                        request.tempoMinGestoMs(),
                        request.tempoMaxGestoMs(),
                        request.urgencia());

        ComandoAuthoringResponse response = ComandoAuthoringResponse.from(result);

        URI location =
                URI.create("/api/pac/v1/pacientes/" + pacienteId + "/comandos/" + response.id());
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(location);
        headers.setETag(response.etag());

        log.debug(
                "POST /pacientes/{}/comandos → 201 id={} etag={}",
                pacienteId,
                response.id(),
                response.etag());
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/pac/v1/pacientes/{pacienteId}/comandos → 200 list
    // -------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.read')")
    public ResponseEntity<List<ComandoAuthoringResponse>> listByPaciente(
            @PathVariable UUID pacienteId) {

        List<ComandoAuthoringResponse> list =
                comandoAuthoringUseCase.listByPaciente(pacienteId).stream()
                        .map(ComandoAuthoringResponse::from)
                        .toList();

        return ResponseEntity.ok(list);
    }

    // -------------------------------------------------------------------------
    // GET /api/pac/v1/pacientes/{pacienteId}/comandos/{id} → 200 + ETag
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.read')")
    public ResponseEntity<ComandoAuthoringResponse> findById(
            @PathVariable UUID pacienteId, @PathVariable UUID id) {

        Comando result = comandoAuthoringUseCase.findByIdAndPacienteId(pacienteId, id);
        ComandoAuthoringResponse response = ComandoAuthoringResponse.from(result);
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // PUT /api/pac/v1/pacientes/{pacienteId}/comandos/{id} → 200 + ETag
    // -------------------------------------------------------------------------

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pac.comando-authoring.editar')")
    @AuditAction("EDIT_COMANDO_DRAFT")
    public ResponseEntity<ComandoAuthoringResponse> editDraft(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ComandoAuthoringRequest request) {

        int expectedRowVersion = parseEtag(ifMatch);

        Comando result =
                comandoAuthoringUseCase.editDraft(
                        pacienteId,
                        id,
                        expectedRowVersion,
                        request.rotulo(),
                        request.necessidade(),
                        request.primitivoPrincipalId(),
                        request.primitivoSecundarioId(),
                        request.sensibilidade(),
                        request.tempoMinGestoMs(),
                        request.tempoMaxGestoMs(),
                        request.urgencia());

        ComandoAuthoringResponse response = ComandoAuthoringResponse.from(result);
        log.debug(
                "PUT /pacientes/{}/comandos/{} → 200 newEtag={}", pacienteId, id, response.etag());
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Computes a SHA-256 hex digest of canonical Comando request body fields (PAC-023).
     *
     * <p>Canonical form includes {@code pacienteId} so two different patients cannot share a
     * comando key collision even if they submit identical body fields.
     */
    private static String hashComandoRequest(UUID pacienteId, ComandoAuthoringRequest req) {
        String canonical =
                pacienteId
                        + "|"
                        + req.rotulo()
                        + "|"
                        + req.necessidade()
                        + "|"
                        + req.primitivoPrincipalId()
                        + "|"
                        + req.primitivoSecundarioId()
                        + "|"
                        + req.sensibilidade()
                        + "|"
                        + req.tempoMinGestoMs()
                        + "|"
                        + req.tempoMaxGestoMs()
                        + "|"
                        + req.urgencia();
        return sha256Hex(canonical);
    }

    /** Computes SHA-256 hex of the given input string using the built-in JCA provider. */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Parses a quoted ETag string into an integer rowVersion.
     *
     * <p>E.g. {@code "\"1\""} → {@code 1}. Returns {@code -1} for absent, blank, or unparseable
     * values — any such result will fail the ETag check in ComandoAuthoringService (409).
     */
    private int parseEtag(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return -1;
        }
        String clean = ifMatch.strip();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
