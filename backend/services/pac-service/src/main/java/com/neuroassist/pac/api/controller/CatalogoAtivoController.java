package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.api.dto.CatalogoAtivoResponse;
import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase;
import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase.CatalogoAtivoResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAC-020 — CatalogoAtivoController: REST surface for the active gesture catalog.
 *
 * <p>Path: {@code /api/pac/v1/pacientes/{pacienteId}/catalogo-ativo}. Consumed by GEST to poll the
 * published commands for a patient.
 *
 * <p>ETag / conditional GET:
 *
 * <ul>
 *   <li>Response includes {@code ETag: "<catalogoVersao>"} header.
 *   <li>If request carries {@code If-None-Match: "<catalogoVersao>"} matching the current catalog
 *       version, returns {@code 304 Not Modified} with no body.
 *   <li>ETag comparison is done without quotes (Spring adds surrounding double-quotes automatically
 *       via {@code ResponseEntity.ok().eTag(...)}).
 * </ul>
 *
 * <p>Architectural rules:
 *
 * <ul>
 *   <li>RULE-04: no infrastructure.* imports.
 *   <li>RULE-03: no @Transactional — boundary is in CatalogoAtivoService.
 *   <li>Never returns JPA @Entity — always CatalogoAtivoResponse DTO.
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/pac/v1/pacientes")
@RequiredArgsConstructor
public class CatalogoAtivoController {

    private final CatalogoAtivoUseCase catalogoAtivoUseCase;

    /**
     * GET /api/pac/v1/pacientes/{pacienteId}/catalogo-ativo
     *
     * <p>Returns all published Comandos for the patient with an ETag based on the patient's {@code
     * catalogoVersao}. Supports conditional GET via {@code If-None-Match}.
     *
     * @param pacienteId the patient whose active catalog is requested
     * @param ifNoneMatch optional {@code If-None-Match} header value from the caller (GEST)
     * @return {@code 200} with catalog payload + ETag, or {@code 304} if catalog unchanged
     */
    @GetMapping("/{pacienteId}/catalogo-ativo")
    @PreAuthorize("hasAuthority('pac.catalogo-ativo.read')")
    public ResponseEntity<CatalogoAtivoResponse> getCatalogoAtivo(
            @PathVariable UUID pacienteId,
            @RequestHeader(value = "X-Empresa-Id", required = false) String empresaId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {

        CatalogoAtivoResult result = catalogoAtivoUseCase.getCatalogoAtivo(pacienteId);
        CatalogoAtivoResponse response = CatalogoAtivoResponse.from(pacienteId, empresaId, result);
        String currentEtag = response.etag();

        // Conditional GET: return 304 if ETag matches
        if (etagMatches(ifNoneMatch, currentEtag)) {
            log.debug(
                    "GET /pacientes/{}/catalogo-ativo → 304 (ETag match, catalogoVersao={})",
                    pacienteId,
                    currentEtag);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(currentEtag).build();
        }

        log.debug(
                "GET /pacientes/{}/catalogo-ativo → 200 catalogoVersao={} comandos={}",
                pacienteId,
                currentEtag,
                response.comandos().size());
        return ResponseEntity.ok().eTag(currentEtag).body(response);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the request's {@code If-None-Match} value matches the current ETag.
     * Spring wraps ETag values in double quotes ({@code "1"}); this method strips them before
     * comparison.
     */
    private static boolean etagMatches(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || currentEtag == null) {
            return false;
        }
        String stripped = ifNoneMatch.replace("\"", "").trim();
        return currentEtag.equals(stripped);
    }
}
