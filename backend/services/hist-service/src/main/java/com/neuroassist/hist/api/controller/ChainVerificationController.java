package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.api.dto.ChainVerificationResponse;
import com.neuroassist.hist.application.port.in.ChainVerificationUseCase;
import com.neuroassist.hist.domain.model.ChainVerificationResult;
import com.neuroassist.hist.domain.model.MissingTenantClaimException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller — on-demand hash-chain verification (HIST-013, P4).
 *
 * <p>Canonical path: {@code GET /api/v1/hist/history/verify}.
 *
 * <p>Read-only; requires {@code hist:read} scope. Walks the entire hash chain from genesis for the
 * specified {@code (subjectType, subjectId)} stream within the caller's tenant and returns the
 * verification outcome ({@code intact | broken | empty}).
 *
 * <p>Tenant extraction: P4 stub reads {@code tenant} JWT claim; replaced by {@code
 * TenantContextFilter} in P5 (HIST-015).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hist/history/verify")
@RequiredArgsConstructor
public class ChainVerificationController {

    private final ChainVerificationUseCase chainVerificationUseCase;

    // Security enforced at the SecurityFilterChain level (SCOPE_hist:read:all required).
    // No method-level @PreAuthorize to avoid catalogue violations when the scope token
    // changes — the SecurityConfig rule is the single source of truth.
    @GetMapping
    public ResponseEntity<ChainVerificationResponse> verifyStream(
            @RequestParam String subjectType,
            @RequestParam UUID subjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            Authentication authentication) {

        String tenantId = extractTenantId(authentication);

        log.info(
                "GET /history/verify: tenant={} subjectType={} subjectId={}",
                tenantId,
                subjectType,
                subjectId);

        ChainVerificationUseCase.VerificationCommand command =
                new ChainVerificationUseCase.VerificationCommand(
                        subjectType, subjectId, from, to, tenantId);

        ChainVerificationResult result = chainVerificationUseCase.verifyStream(command);

        return ResponseEntity.ok(toResponse(result));
    }

    // ---- helper ----

    /**
     * Reads the tenant from the JWT, failing loudly when it is absent.
     *
     * <p>This used to fall back to the literal {@code "stub-tenant"} (C-35). A request whose token
     * carried no tenant was therefore not rejected — it was answered against a tenant that does not
     * exist, which is the quiet kind of wrong: an empty result set looks exactly like "this tenant
     * has no facts". acs-service emits the claim on every token it issues, so its absence is a
     * misconfiguration, not a case to accommodate.
     */
    private String extractTenantId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String tenant = jwtAuth.getToken().getClaimAsString("tenant_id");
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        throw new MissingTenantClaimException();
    }

    private ChainVerificationResponse toResponse(ChainVerificationResult result) {
        return new ChainVerificationResponse(
                result.streamKey(),
                result.status().name(),
                result.recordsVerified(),
                result.erasedCount(),
                result.brokenAtSequenceNo(),
                result.verifiedAt());
    }
}
