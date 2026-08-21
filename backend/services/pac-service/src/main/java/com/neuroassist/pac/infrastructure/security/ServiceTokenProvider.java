package com.neuroassist.pac.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Obtains and caches service-identity tokens from acs-service ({@code POST /oauth2/token/service},
 * ACS-030) — see {@code docs/architecture/backend/standards/service-identity.md}.
 *
 * <p>Why this exists: until now every outbound Feign call propagated the caller's JWT. For PAC →
 * HIST that forced the {@code CUIDADORA} role to carry {@code hist:write}, i.e. a caregiver could
 * write an arbitrary audit fact. With service identity, pac-service calls hist-service <b>as
 * pac-service</b>; the caregiver stays the <i>actor</i> recorded inside the fact body, but is no
 * longer the <i>authority</i> of the call.
 *
 * <p><b>Cache.</b> Keyed by tenant — the token is tenant-scoped because downstream services route
 * the Postgres {@code search_path} by the tenant claim. An entry is reused until {@code
 * refreshMargin} (default 60s) before its expiry, then transparently re-minted. Thread-safe: reads
 * hit a {@link ConcurrentHashMap}; a miss takes a per-tenant lock so concurrent requests for the
 * same tenant mint the token once (and requests for other tenants never block).
 *
 * <p><b>LGPD / security.</b> Neither the client secret nor any token value is ever logged. Failures
 * raise {@link ServiceTokenUnavailableException} — never a fallback to the caller's token.
 */
@Slf4j
@Component
public class ServiceTokenProvider {

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Duration refreshMargin;
    private final Clock clock;

    private final ConcurrentMap<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public ServiceTokenProvider(
            RestClient.Builder restClientBuilder,
            @Value("${neuroassist.service-identity.token-uri}") String tokenUri,
            @Value("${neuroassist.service-identity.client-id:pac-service}") String clientId,
            @Value("${neuroassist.service-identity.client-secret:}") String clientSecret,
            @Value("${neuroassist.service-identity.refresh-margin-seconds:60}")
                    long refreshMarginSeconds,
            @Value("${neuroassist.service-identity.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${neuroassist.service-identity.read-timeout-ms:3000}") int readTimeoutMs) {
        this(
                restClientBuilder
                        .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                        .build(),
                tokenUri,
                clientId,
                clientSecret,
                Duration.ofSeconds(refreshMarginSeconds),
                Clock.systemUTC());
    }

    /** Constructor used by tests — injects an arbitrary {@link RestClient} and {@link Clock}. */
    public ServiceTokenProvider(
            RestClient restClient,
            String tokenUri,
            String clientId,
            String clientSecret,
            Duration refreshMargin,
            Clock clock) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshMargin = refreshMargin;
        this.clock = clock;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a valid service token for the tenant currently on the thread.
     *
     * @throws IllegalStateException if no tenant is bound (RULE-07 — never defaulted)
     * @throws ServiceTokenUnavailableException if ACS cannot mint the token
     */
    public String currentTenantToken() {
        return tokenFor(TenantContextHolder.require());
    }

    /**
     * Returns a valid service token for {@code tenantId}, minting one if the cache is empty or the
     * cached token is within the refresh margin of expiry.
     *
     * @throws ServiceTokenUnavailableException if ACS cannot mint the token
     */
    public String tokenFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException(
                    "Service token requested without a tenant — RULE-07 violation");
        }
        CachedToken cached = cache.get(tenantId);
        if (isUsable(cached)) {
            return cached.accessToken();
        }
        Object lock = locks.computeIfAbsent(tenantId, key -> new Object());
        synchronized (lock) {
            CachedToken recheck = cache.get(tenantId);
            if (isUsable(recheck)) {
                return recheck.accessToken();
            }
            CachedToken fresh = mint(tenantId);
            cache.put(tenantId, fresh);
            return fresh.accessToken();
        }
    }

    /** Drops every cached token — test/ops hook, e.g. after a secret rotation. */
    public void evictAll() {
        cache.clear();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isUsable(CachedToken token) {
        return token != null && token.isValidAt(clock.instant(), refreshMargin);
    }

    private CachedToken mint(String tenantId) {
        ServiceTokenResponse response;
        try {
            response =
                    restClient
                            .post()
                            .uri(tokenUri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new ServiceTokenRequest(clientId, clientSecret, tenantId))
                            .retrieve()
                            .body(ServiceTokenResponse.class);
        } catch (RestClientException ex) {
            // ex.getMessage() carries status/URI only — never the secret (it is in the body).
            throw new ServiceTokenUnavailableException(
                    "acs-service refused or failed to issue a service token for clientId="
                            + clientId
                            + " tenant="
                            + tenantId
                            + ": "
                            + ex.getMessage(),
                    ex);
        }
        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()) {
            throw new ServiceTokenUnavailableException(
                    "acs-service returned an empty service token for clientId="
                            + clientId
                            + " tenant="
                            + tenantId);
        }
        Instant expiresAt = clock.instant().plusSeconds(Math.max(response.expiresIn(), 0L));
        log.debug(
                "Service token minted for clientId={} tenant={} expiresIn={}s",
                clientId,
                tenantId,
                response.expiresIn());
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    /**
     * A minted token plus its absolute expiry.
     *
     * <p>{@code toString()} is overridden so the token never reaches a log line by accident.
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
