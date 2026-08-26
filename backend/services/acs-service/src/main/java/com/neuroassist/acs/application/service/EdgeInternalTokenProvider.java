package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Supplies acs-service's own {@code edge.internal} service token for the ACS→EDGE credential
 * verification call (E2E-005, {@code standards/service-identity.md} §5 and §7).
 *
 * <p><b>Why it mints in-process.</b> Consumers of service identity in other services (see {@code
 * ServiceTokenProvider} in pac-service and edge-service) call {@code POST /oauth2/token/service}
 * over HTTP. acs-service <i>is</i> that endpoint, so it goes straight to {@link
 * ServiceTokenService} instead of looping back through its own web layer. The security-relevant
 * part is unchanged: the configured secret is matched against the BCrypt hash in {@code
 * acs.service-clients}, so a missing or wrong {@code ACS_CLIENT_SECRET} fails closed exactly as it
 * would over the wire.
 *
 * <p><b>The tenant chicken-and-egg, and how it is resolved.</b> A service token is normally scoped
 * to a tenant, because downstream services route the Postgres {@code search_path} by the tenant
 * claim. Here the tenant is precisely what the call is trying to find out: the device sends only
 * {@code clientId}/{@code clientSecret}, and its tenant is known only after edge-service answers.
 * Three options were on the table and the choice is deliberate:
 *
 * <ol>
 *   <li><i>Take a tenant from the request body.</i> Rejected — the caller is unauthenticated, so
 *       that value is attacker-controlled, and it would have to be ignored anyway when issuing the
 *       device token (whose tenant comes from edge-service).
 *   <li><i>Default to some tenant.</i> Rejected — a silent default is how cross-tenant bugs start.
 *   <li><b>Issue a tenant-less token</b> — chosen. {@code POST /internal/devices/verify-credential}
 *       resolves the tenant itself through the global {@code device_directory} index (§7.4), and
 *       edge-service's {@code TenantContextFilter} binds a tenant only when the claim is present,
 *       so the omission is honest rather than papered over: the token grants {@code edge.internal}
 *       and carries no tenant authority at all.
 * </ol>
 *
 * <p>The cache is nevertheless keyed by tenant, mirroring the other providers, so that a future
 * tenant-scoped internal call needs no restructuring; the device flow uses the {@link
 * #NO_TENANT_KEY tenant-less} entry.
 *
 * <p><b>Security.</b> Neither the client secret nor any token value is ever logged.
 */
@Slf4j
@Service
public class EdgeInternalTokenProvider {

    /** Cache key for the tenant-less token — a literal, never a tenant id. */
    static final String NO_TENANT_KEY = "__no_tenant__";

    private final ServiceTokenService serviceTokenService;
    private final String clientId;
    private final String clientSecret;
    private final Duration refreshMargin;
    private final Clock clock;

    private final ConcurrentMap<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public EdgeInternalTokenProvider(
            ServiceTokenService serviceTokenService,
            @Value("${acs.service-identity.client-id:acs-service}") String clientId,
            @Value("${acs.service-identity.client-secret:}") String clientSecret,
            @Value("${acs.service-identity.refresh-margin-seconds:60}") long refreshMarginSeconds) {
        this(
                serviceTokenService,
                clientId,
                clientSecret,
                Duration.ofSeconds(refreshMarginSeconds),
                Clock.systemUTC());
    }

    /** Constructor used by tests — injects an arbitrary {@link Clock}. */
    public EdgeInternalTokenProvider(
            ServiceTokenService serviceTokenService,
            String clientId,
            String clientSecret,
            Duration refreshMargin,
            Clock clock) {
        this.serviceTokenService = serviceTokenService;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshMargin = refreshMargin;
        this.clock = clock;
    }

    /**
     * Returns a valid {@code edge.internal} token with no tenant claim — the identity used to
     * verify a device credential before its tenant is known.
     */
    public String tenantlessToken() {
        return cached(NO_TENANT_KEY);
    }

    /**
     * Returns a valid token <b>scoped to {@code tenantId}</b> (E2E-019).
     *
     * <p>Necessário porque nem todo destino trata o tenant do mesmo jeito. O edge-service resolve o
     * tenant sozinho pelo índice global de dispositivos, então aceita um token sem claim; o
     * hist-service roteia o {@code search_path} <i>pelo claim</i> — um fato de login gravado com
     * token sem tenant não chegaria a schema nenhum.
     *
     * <p>O cache já era chaveado por tenant justamente para isto.
     */
    public String tokenForTenant(java.util.UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for a tenant-scoped token");
        }
        return cached(tenantId.toString());
    }

    /** Drops every cached token — test/ops hook, e.g. after a secret rotation. */
    public void evictAll() {
        cache.clear();
    }

    private String cached(String key) {
        CachedToken hit = cache.get(key);
        if (isUsable(hit)) {
            return hit.accessToken();
        }
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            CachedToken recheck = cache.get(key);
            if (isUsable(recheck)) {
                return recheck.accessToken();
            }
            CachedToken fresh = mint(key);
            cache.put(key, fresh);
            return fresh.accessToken();
        }
    }

    private boolean isUsable(CachedToken token) {
        return token != null && token.isValidAt(clock.instant(), refreshMargin);
    }

    private CachedToken mint(String key) {
        AutenticacaoResponse response =
                NO_TENANT_KEY.equals(key)
                        ? serviceTokenService.issueTenantlessToken(clientId, clientSecret)
                        : serviceTokenService.issueToken(
                                new com.neuroassist.acs.api.dto.ServiceTokenRequest(
                                        clientId, clientSecret, java.util.UUID.fromString(key)));
        Instant expiresAt = clock.instant().plusSeconds(Math.max(response.expiresIn(), 0L));
        log.debug(
                "Internal service token minted clientId={} expiresIn={}s",
                clientId,
                response.expiresIn());
        return new CachedToken(response.accessToken(), expiresAt);
    }

    /**
     * A minted token plus its absolute expiry. {@code toString()} is overridden so the token value
     * can never reach a log line by accident.
     */
    private record CachedToken(String accessToken, Instant expiresAt) {

        boolean isValidAt(Instant now, Duration margin) {
            return expiresAt.minus(margin).isAfter(now);
        }

        @Override
        public String toString() {
            return "CachedToken[expiresAt=" + expiresAt + "]";
        }
    }
}
