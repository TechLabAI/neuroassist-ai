package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.api.dto.ChainVerificationResponse;
import com.neuroassist.hist.application.port.in.ChainVerificationUseCase;
import com.neuroassist.hist.domain.model.ChainVerificationResult;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_hist:read')")
    public ResponseEntity<ChainVerificationResponse> verifyStream(
            @RequestParam String subjectType,
            @RequestParam UUID subjectId,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
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

    private String extractTenantId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String tenant = jwtAuth.getToken().getClaimAsString("tenant");
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        return "stub-tenant";
    }

    private ChainVerificationResponse toResponse(ChainVerificationResult result) {
        return new ChainVerificationResponse(
                result.streamKey(),
                result.status().name(),
                result.recordsVerified(),
                result.brokenAtSequenceNo(),
                result.verifiedAt());
    }
}

