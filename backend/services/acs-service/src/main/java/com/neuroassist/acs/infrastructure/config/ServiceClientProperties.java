package com.neuroassist.acs.infrastructure.config;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Registry of service clients allowed to obtain a service-identity token (ACS-030).
 *
 * <p>Bound from {@code acs.service-clients.<clientId>} in application.yml:
 *
 * <pre>
 * acs:
 *   service-clients:
 *     edge-service:
 *       secret-hash: ${EDGE_CLIENT_SECRET_HASH}
 *       authorities:
 *         - gest.detection-profile.read
 * </pre>
 *
 * <p>Config-driven rather than table-driven: rotating a secret means changing an env var and
 * restarting, and there is no runtime CRUD to maintain. Moving to a {@code service_client} table
 * later only changes the lookup behind {@code ServiceTokenService} — the token contract is
 * unaffected.
 *
 * <p>SECURITY: {@code secretHash} is a BCrypt hash, never a plaintext secret. A client whose hash
 * is blank is treated as disabled, so a missing env var fails closed instead of accepting any
 * secret.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acs")
public class ServiceClientProperties {

    /** clientId → credentials + granted authorities. Empty map means no service client exists. */
    private Map<String, ServiceClient> serviceClients = Map.of();

    @Getter
    @Setter
    public static class ServiceClient {

        /** BCrypt hash of the client secret. Blank disables the client (fail closed). */
        private String secretHash;

        /**
         * Authorities embedded in the issued token. Must match, verbatim, the authority names the
         * target service enforces — e.g. {@code gest.detection-profile.read} for gest-service or
         * {@code hist:write} for hist-service (which applies the {@code SCOPE_} prefix itself).
         */
        private List<String> authorities = List.of();

        /** Set false to disable the client without removing its configuration. */
        private boolean enabled = true;
    }
}
