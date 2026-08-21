package com.neuroassist.edge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ServiceTokenProvider} — per-tenant cache, renewal margin, fail-closed
 * behaviour (service-identity contract §5, defects C-18/C-19).
 */
@DisplayName("ServiceTokenProvider — per-tenant cache, renewal margin and fail-closed")
class ServiceTokenProviderTest {

    private static final String TENANT_A = "e2e00000-0000-0000-0000-000000000001";
    private static final String TENANT_B = "e2e00000-0000-0000-0000-000000000002";

    /** Mutable clock so expiry can be simulated without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    /** Records every tenant asked for and hands out a distinct token each time. */
    private static final class RecordingClient implements ServiceTokenClient {
        private final ConcurrentLinkedQueue<String> calls = new ConcurrentLinkedQueue<>();
        private final AtomicInteger seq = new AtomicInteger();
        private long expiresIn = 900;

        @Override
        public ServiceTokenResponse requestToken(String tenantId) {
            calls.add(tenantId);
            return new ServiceTokenResponse(
                    "jwt-" + tenantId + "-" + seq.incrementAndGet(), "Bearer", expiresIn);
        }

        List<String> calls() {
            return List.copyOf(calls);
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Caches per tenant — a second call for the same tenant hits no ACS")
    void cachesPerTenant() {
        RecordingClient client = new RecordingClient();
        ServiceTokenProvider provider = new ServiceTokenProvider(client, 60, new MutableClock());

        String first = provider.tokenFor(TENANT_A);
        String second = provider.tokenFor(TENANT_A);

        assertThat(second).isEqualTo(first);
        assertThat(client.calls()).containsExactly(TENANT_A);
    }

    @Test
    @DisplayName("Cache key is the tenant — a different tenant gets a different token")
    void differentTenantDifferentToken() {
        RecordingClient client = new RecordingClient();
        ServiceTokenProvider provider = new ServiceTokenProvider(client, 60, new MutableClock());

        String a = provider.tokenFor(TENANT_A);
        String b = provider.tokenFor(TENANT_B);

        assertThat(a).isNotEqualTo(b);
        assertThat(client.calls()).containsExactly(TENANT_A, TENANT_B);
    }

    @Test
    @DisplayName("Renews 60s before expiry — still cached at 839s, renewed at 841s")
    void renewsWithMargin() {
        RecordingClient client = new RecordingClient();
        MutableClock clock = new MutableClock();
        ServiceTokenProvider provider = new ServiceTokenProvider(client, 60, clock);

        String first = provider.tokenFor(TENANT_A); // expiresIn=900 -> renew at +840s

        clock.advance(Duration.ofSeconds(839));
        assertThat(provider.tokenFor(TENANT_A)).isEqualTo(first);
        assertThat(client.calls()).hasSize(1);

        clock.advance(Duration.ofSeconds(2)); // 841s — inside the 60s margin
        assertThat(provider.tokenFor(TENANT_A)).isNotEqualTo(first);
        assertThat(client.calls()).hasSize(2);
    }

    @Test
    @DisplayName("No tenant in context — fails closed, no default tenant is invented")
    void noTenantFailsClosed() {
        RecordingClient client = new RecordingClient();
        ServiceTokenProvider provider = new ServiceTokenProvider(client, 60, new MutableClock());

        assertThatThrownBy(provider::tokenForCurrentTenant)
                .isInstanceOf(ServiceTokenUnavailableException.class)
                .hasMessageContaining("No tenant in context");
        assertThat(client.calls()).isEmpty();
    }

    @Test
    @DisplayName("Uses the tenant of the request in course")
    void usesCurrentTenant() {
        RecordingClient client = new RecordingClient();
        ServiceTokenProvider provider = new ServiceTokenProvider(client, 60, new MutableClock());

        TenantContextHolder.set(TENANT_B);
        assertThat(provider.tokenForCurrentTenant()).contains(TENANT_B);
        assertThat(client.calls()).containsExactly(TENANT_B);
    }

    @Test
    @DisplayName("ACS failure surfaces as ServiceTokenUnavailableException — never a fallback")
    void acsFailureFailsClosed() {
        ServiceTokenClient failing =
                tenantId -> {
                    throw new IllegalStateException("connection refused");
                };
        ServiceTokenProvider provider = new ServiceTokenProvider(failing, 60, new MutableClock());

        assertThatThrownBy(() -> provider.tokenFor(TENANT_A))
                .isInstanceOf(ServiceTokenUnavailableException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Empty access token from ACS fails closed")
    void emptyTokenFailsClosed() {
        ServiceTokenClient empty = tenantId -> new ServiceTokenResponse("  ", "Bearer", 900);
        ServiceTokenProvider provider = new ServiceTokenProvider(empty, 60, new MutableClock());

        assertThatThrownBy(() -> provider.tokenFor(TENANT_A))
                .isInstanceOf(ServiceTokenUnavailableException.class);
    }

    @Test
    @DisplayName("Concurrent misses for the same tenant produce a single ACS request")
    void concurrentMissesFetchOnce() throws Exception {
        RecordingClient client = new RecordingClient();
        ServiceTokenProvider provider = new ServiceTokenProvider(client, 60, new MutableClock());

        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(
                        () -> {
                            try {
                                start.await();
                                provider.tokenFor(TENANT_A);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                done.countDown();
                            }
                        });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(client.calls()).containsExactly(TENANT_A);
    }

    @Test
    @DisplayName("invalidate() forces a fresh token on the next call")
    void invalidateForcesRefetch() {
        RecordingClient client = new RecordingClient();
        ServiceTokenProvider provider = new ServiceTokenProvider(client, 60, new MutableClock());

        String first = provider.tokenFor(TENANT_A);
        provider.invalidate(TENANT_A);

        assertThat(provider.tokenFor(TENANT_A)).isNotEqualTo(first);
        assertThat(client.calls()).hasSize(2);
    }

    @Test
    @DisplayName("Token value never appears in the response record's toString()")
    void tokenIsRedactedInToString() {
        assertThat(new ServiceTokenResponse("super-secret-jwt", "Bearer", 900).toString())
                .doesNotContain("super-secret-jwt")
                .contains("***");
    }
}
