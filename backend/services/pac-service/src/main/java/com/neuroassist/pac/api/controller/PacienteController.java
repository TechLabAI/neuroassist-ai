package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.api.dto.PacienteRequest;
import com.neuroassist.pac.api.dto.PacienteResponse;
import com.neuroassist.pac.application.port.in.IdempotencyUseCase;
import com.neuroassist.pac.application.port.in.PacienteUseCase;
import com.neuroassist.pac.domain.model.Paciente;
import com.neuroassist.pac.shared.audit.AuditAction;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAC-005 — PacienteController: REST surface for Paciente CRUD.
 *
 * <p>Paths aligned to feature: /api/pac/v1/pacientes. RULE-04: no infrastructure imports. RULE-05:
 * no api↔infrastructure cross-dependency. No @Transactional (RULE-03 — boundary is in
 * PacienteService). Never returns JPA entities — always DTO records.
 *
 * <p>ETag format: RFC 7232 strong ETag — e.g. {@code "1"} (quoted integer = rowVersion + 1).
 */
@Slf4j
@RestController
@RequestMapping("/api/pac/v1/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private static final String RESOURCE_TYPE_PACIENTE = "paciente";

    private final PacienteUseCase pacienteUseCase;
    private final IdempotencyUseCase idempotencyUseCase;

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes → 201 Created + Location + ETag
    // -------------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAuthority('pac.paciente.criar')")
    @AuditAction("CREATE_PACIENTE")
    public ResponseEntity<PacienteResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PacienteRequest request) {

        // PAC-023 — Idempotency-Key deduplication (SPEC-05 §3)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String bodyHash = hashPacienteRequest(request);
            Optional<UUID> existing =
                    idempotencyUseCase.checkKey(idempotencyKey, RESOURCE_TYPE_PACIENTE, bodyHash);
            if (existing.isPresent()) {
                // Replay: return 200 with original resource (SPEC-05 §3.2 — same key + same body)
                log.debug("POST /pacientes idempotency hit key={} → 200", idempotencyKey);
                Paciente original = pacienteUseCase.findById(existing.get());
                return ResponseEntity.ok(PacienteResponse.from(original));
            }

            // New key: proceed with creation, then register
            Paciente result =
                    pacienteUseCase.create(
                            request.nome(),
                            request.dispositivoEdgeId(),
                            request.cuidadorResponsavelIds());
            idempotencyUseCase.registerKey(
                    idempotencyKey, RESOURCE_TYPE_PACIENTE, result.id(), bodyHash);

            PacienteResponse response = PacienteResponse.from(result);
            URI location = URI.create("/api/pac/v1/pacientes/" + response.id());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(location);
            headers.setETag(response.etag());
            log.debug(
                    "POST /pacientes → 201 id={} etag={} idempotencyKey={}",
                    response.id(),
                    response.etag(),
                    idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(response);
        }

        // No Idempotency-Key header: standard creation
        Paciente result =
                pacienteUseCase.create(
                        request.nome(),
                        request.dispositivoEdgeId(),
                        request.cuidadorResponsavelIds());
        PacienteResponse response = PacienteResponse.from(result);

        URI location = URI.create("/api/pac/v1/pacientes/" + response.id());
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(location);
        headers.setETag(response.etag());

        log.debug("POST /pacientes → 201 id={} etag={}", response.id(), response.etag());
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/pac/v1/pacientes → 200 + list (tenant-scoped)
    // -------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAuthority('pac.paciente.read')")
    public ResponseEntity<List<PacienteResponse>> listAll() {
        List<PacienteResponse> list =
                pacienteUseCase.listAll().stream().map(PacienteResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    // -------------------------------------------------------------------------
    // GET /api/pac/v1/pacientes/sem-cobertura → 200
    // -------------------------------------------------------------------------

    /**
     * Pacientes da empresa sem nenhuma cuidadora ativa (D-204 / ADR-010).
     *
     * <p>Existe porque um paciente descoberto e <b>invisivel</b> ao painel: o painel lista os
     * pacientes de uma cuidadora pelo directory do ACS, e quem nao tem vinculo nao esta na lista
     * de ninguem. Sem este endpoint, "criar paciente sem cuidadora" seria permitido e ninguem
     * jamais saberia — que e exatamente o que a decisao de produto quis evitar.
     *
     * <p><b>Declarado antes de {@code /&#123;id&#125;} de proposito.</b> O Spring casa por
     * especificidade e nao pela ordem de declaracao, entao isto e cosmetico — mas a ordem no
     * arquivo evita que a proxima pessoa se pergunte se {@code sem-cobertura} nao seria capturado
     * como um id.
     */
    @GetMapping("/sem-cobertura")
    @PreAuthorize("hasAuthority('pac.paciente.read')")
    public ResponseEntity<List<PacienteResponse>> listUncovered() {
        List<PacienteResponse> list =
                pacienteUseCase.listUncovered().stream().map(PacienteResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    // -------------------------------------------------------------------------
    // GET /api/pac/v1/pacientes/{id} → 200 + ETag
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pac.paciente.read')")
    public ResponseEntity<PacienteResponse> findById(@PathVariable UUID id) {
        Paciente result = pacienteUseCase.findById(id);
        PacienteResponse response = PacienteResponse.from(result);

        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/pac/v1/pacientes/{id} → 200  (LGPD erasure, Flow-04 / E2E-012)
    // -------------------------------------------------------------------------

    /**
     * Erases the patient's personal data on request (LGPD).
     *
     * <p><b>Not a delete, despite the verb.</b> The row and its id survive: {@code comando} and
     * {@code paciente_cuidador} reference them, and hist-service keeps the audit records and the
     * hash chain over them. What goes away is the personal data — {@code nome} here, and every
     * encrypted audit payload about this patient in hist-service, whose key is destroyed in the
     * same operation. DELETE is still the honest verb for "make this person's data go away"; the
     * response body shows what remains.
     *
     * <p>Returns 200 with the anonymised resource rather than 204, so the caller can see the {@code
     * erasedAt} timestamp it just caused.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('pac.paciente.deletar')")
    @AuditAction("ERASE_PACIENTE")
    public ResponseEntity<PacienteResponse> erasePersonalData(@PathVariable UUID id) {
        Paciente result = pacienteUseCase.erasePersonalData(id);
        PacienteResponse response = PacienteResponse.from(result);
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // PUT /api/pac/v1/pacientes/{id} → 200 + ETag  (or 409 if stale)
    // -------------------------------------------------------------------------

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pac.paciente.editar')")
    @AuditAction("UPDATE_PACIENTE")
    public ResponseEntity<PacienteResponse> update(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody PacienteRequest request) {

        int expectedVersion = parseEtag(ifMatch);

        Paciente result =
                pacienteUseCase.update(
                        id,
                        expectedVersion,
                        request.nome(),
                        request.dispositivoEdgeId(),
                        request.cuidadorResponsavelIds());
        PacienteResponse response = PacienteResponse.from(result);

        log.debug("PUT /pacientes/{} → 200 newEtag={}", id, response.etag());
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Computes a SHA-256 hex digest of the canonical request body fields (PAC-023).
     *
     * <p>Canonical form: {@code nome|dispositivoEdgeId|sortedCuidadorIds}. UUID lists are sorted to
     * make the hash order-independent.
     */
    private static String hashPacienteRequest(PacienteRequest req) {
        String cuidadores =
                req.cuidadorResponsavelIds() == null
                        ? ""
                        : req.cuidadorResponsavelIds().stream()
                                .sorted(Comparator.comparing(UUID::toString))
                                .map(UUID::toString)
                                .collect(Collectors.joining(","));
        String canonical = req.nome() + "|" + req.dispositivoEdgeId() + "|" + cuidadores;
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
     * Parses a quoted ETag string into an integer.
     *
     * <p>E.g. {@code "\"1\""} → {@code 1}. Returns {@code -1} for absent, blank, or unparseable
     * values — any such result will fail the ETag check in the service (409 conflict-precondition).
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
