package com.neuroassist.hist.domain.model;

/**
 * Thrown when the {@code empresaId} in the request body does not match the tenant from the JWT.
 * Maps to HTTP 403 / {@code type: /errors/tenant-mismatch}. Zero Spring/JPA imports (RULE-01,
 * RULE-02).
 */
public class TenantMismatchException extends RuntimeException {

    public TenantMismatchException(String jwtTenant, String bodyTenant) {
        super(
                "Request body empresaId '"
                        + bodyTenant
                        + "' does not match the JWT tenant '"
                        + jwtTenant
                        + "'.");
    }

    public String errorCode() {
        return "tenant-mismatch";
    }
}
