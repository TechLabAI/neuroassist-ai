package com.neuroassist.hist.domain.model;

/**
 * Thrown when an authenticated request carries no {@code tenant} claim.
 *
 * <p>Replaces a fallback to the literal {@code "stub-tenant"} (C-35). Answering such a request
 * against a tenant that does not exist is worse than refusing it: the caller gets an empty result
 * set, which is indistinguishable from a tenant that genuinely has no facts.
 *
 * <p>Maps to HTTP 403. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class MissingTenantClaimException extends RuntimeException {

    public MissingTenantClaimException() {
        super("Token carries no 'tenant' claim — the request cannot be scoped to a tenant.");
    }

    public String errorCode() {
        return "missing-tenant-claim";
    }
}
