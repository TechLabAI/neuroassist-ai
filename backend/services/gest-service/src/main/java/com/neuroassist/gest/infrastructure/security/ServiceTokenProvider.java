package com.neuroassist.gest.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Obtains and caches the GEST service-identity token per tenant (service-identity contract §5).
 *
 * <p>The token is scoped to a tenant because downstream services route the Postgres {@code
 * search_path} by the tenant claim — hence the cache key is the tenant id.
 *
 * <p>Renewal happens {@code refresh-margin-seconds} before the advertised expiry. There is no
 * refresh token: on expiry the provider reauthenticates with the client secret.
 *
 * <p>Thread-safety: {@link ConcurrentHashMap#compute} serialises concurrent misses for the same
 * tenant, so a burst of heartbeats produces one token request, not N.
 *
 * <p><b>Fail-closed:</b> every failure surfaces as {@link ServiceTokenUnavailableException}. The
 * token itself is never logged.
 */
@Slf4j
@Component
public class ServiceTokenProvider {

    private final ServiceTokenClient client;
    private final long refreshMarginSeconds;
    private final Clock clock;
    private final ConcurrentMap<String, CachedToken> cache = new ConcurrentHashMap<>();

    @Autowired
    public ServiceTokenProvider(
            ServiceTokenClient client,
            @Value("${neuroassist.acs.service-token.refresh-margin-seconds:60}")
                    long refreshMarginSeconds) {
        this(client, refreshMarginSeconds, Clock.systemUTC());
    }

    /** Test seam — injectable clock. */
    ServiceTokenProvider(ServiceTokenClient client, long refreshMarginSeconds, Clock clock) {
        this.client = client;
        this.refreshMarginSeconds = Math.max(0, refreshMarginSeconds);
        this.clock = clock;
    }

    /**
     * Token for the tenant of the request currently being served (RULE-07).
     *
     * @throws ServiceTokenUnavailableException when there is no tenant in context — no default
     *     tenant is ever invented — or when the ACS call fails
     */
    public String tokenForCurrentTenant() {
        String tenantId = TenantContextHolder.getTenantIdOrNull();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ServiceTokenUnavailableException(
                    "No tenant in context — cannot request a tenant-scoped service token"
                            + " (RULE-07)");
        }
        return tokenFor(tenantId);
    }

    /** Token for an explicit tenant. */
    public String tokenFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ServiceTokenUnavailableException("tenantId is required for a service token");
        }

        CachedToken cached = cache.get(tenantId);
        if (cached != null && cached.usableAt(clock.instant())) {
            return cached.accessToken();
        }

        CachedToken fresh =
                cache.compute(
                        tenantId,
                        (tenant, existing) ->
                                existing != null && existing.usableAt(clock.instant())
                                        ? existing
                                        : fetch(tenant));
        return fresh.accessToken();
    }

    /** Drops the cached token for a tenant (e.g. after a downstream 401). */
    public void invalidate(String tenantId) {
        if (tenantId != null) {
            cache.remove(tenantId);
        }
    }

    private CachedToken fetch(String tenantId) {
        try {
            ServiceTokenResponse response = client.requestToken(tenantId);
            if (response == null
                    || response.accessToken() == null
                    || response.accessToken().isBlank()) {
                throw new ServiceTokenUnavailableException(
                        "ACS returned an empty service token for tenant " + tenantId);
            }
            Instant now = clock.instant();
            Instant renewAt =
                    now.plusSeconds(Math.max(0, response.expiresIn() - refreshMarginSeconds));
            log.debug(
                    "Service token obtained for tenant={} expiresIn={}s (renew in {}s)",
                    tenantId,
                    response.expiresIn(),
                    Math.max(0, response.expiresIn() - refreshMarginSeconds));
            return new CachedToken(response.accessToken(), renewAt);
        } catch (ServiceTokenUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // No token value, no secret — only the failure class/message of the transport error.
            log.error(
                    "Could not obtain service token for tenant={}: {}",
                    tenantId,
                    e.getClass().getSimpleName());
            throw new ServiceTokenUnavailableException(
                    "Could not obtain service token for tenant " + tenantId, e);
        }
    }

    /** Cached token plus the instant from which it must be renewed. */
    private record CachedToken(String accessToken, Instant renewAt) {
        boolean usableAt(Instant now) {
            return now.isBefore(renewAt);
        }
    }
}
