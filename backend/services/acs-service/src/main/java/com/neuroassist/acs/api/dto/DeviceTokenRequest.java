package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO — device-identity token ({@code POST /oauth2/token/device}, E2E-005).
 *
 * <p>Contract of {@code standards/service-identity.md} §7.3, deliberately kept to the two fields
 * the device received at enroll. There is <b>no</b> {@code tenantId} field, unlike {@link
 * ServiceTokenRequest}: the tenant is whatever edge-service reports for this credential, and
 * accepting a tenant from an unauthenticated caller would only create a value that must then be
 * ignored.
 *
 * <p>LGPD / security: {@code clientSecret} is request-only. It is never echoed in a response, never
 * persisted by acs-service and never written to a log at any level.
 */
public record DeviceTokenRequest(

        /** Device client id minted at enroll, of the form {@code edge-<deviceId>}. */
        @NotBlank(message = "clientId is required") String clientId,

        /** Device secret; verified by edge-service, which holds its SHA-256 hash. */
        @NotBlank(message = "clientSecret is required") String clientSecret) {}
