package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request DTO — service-identity token ({@code POST /oauth2/token/service}, ACS-030).
 *
 * <p>A separate endpoint from {@code /oauth2/token} rather than a {@code client_credentials} grant
 * on the same one: a service token is always scoped to a tenant, so it needs a field the caregiver
 * flow has no use for, and overloading {@code login}/{@code senha} to mean {@code clientId}/{@code
 * clientSecret} would make both contracts harder to read.
 *
 * <p>LGPD: {@code clientSecret} is request-only and never echoed in a response or a log.
 */
public record ServiceTokenRequest(

        /** Identifier of the calling service, e.g. {@code edge-service}. */
        @NotBlank(message = "clientId is required") String clientId,

        /** Shared secret; validated against the BCrypt hash in {@code acs.service-clients}. */
        @NotBlank(message = "clientSecret is required") String clientSecret,

        /**
         * Tenant the token is scoped to. Required for every client except one declared {@code
         * tenantless} in {@code acs.service-clients} (E2E-029).
         *
         * <p>The requirement is not ceremony: downstream services route the Postgres {@code
         * search_path} by the tenant claim, so a tenant-less token would reach the wrong schema.
         * The single exception is the client that creates tenants, whose call happens before the
         * tenant exists. {@code ServiceTokenService} enforces this — a client that is not declared
         * tenant-less and omits the field is rejected with the same opaque 401 as a bad secret.
         *
         * <p>When present the tenant must exist and be ACTIVE.
         */
        UUID tenantId) {}
