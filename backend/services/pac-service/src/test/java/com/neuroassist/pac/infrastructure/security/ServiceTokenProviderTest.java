package com.neuroassist.pac.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@link ServiceTokenProvider} — caching, refresh margin, tenant scoping and fail-closed behaviour
 * (standards/service-identity.md §3 and §5).
 */
class ServiceTokenProviderTest {

    private static final String TOKEN_URI = "http://acs-service:8088/oauth2/token/service";
    private static final String TENANT_A = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String TENANT_B = "bbbbbbbb-0000-0000-0000-000000000002";

    private MutableClock clock;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ServiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider =
                new ServiceTokenProvider(
                        builder.build(),
                        TOKEN_URI,
                        "pac-service",
                        "dev-service-secret",
                        Duration.ofSeconds(60),
                        clock);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("posts clientId/clientSecret/tenantId to the ACS service-token endpoint")
    void postsTheDocumentedContract() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.clientId").value("pac-service"))
                .andExpect(jsonPath("$.clientSecret").value("dev-service-secret"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andRespond(tokenResponse("token-a", 900));

        assertThat(provider.tokenFor(TENANT_A)).isEqualTo("token-a");
        server.verify();
    }

    @Test
    @DisplayName("caches per tenant — a second call within the validity window mints nothing")
    void cachesPerTenant() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
                .andRespond(tokenResponse("token-a", 900));

        assertThat(provider.tokenFor(TENANT_A)).isEqualTo("token-a");
        clock.advance(Duration.ofSeconds(300));
        assertThat(provider.tokenFor(TENANT_A)).isEqualTo("token-a");
        server.verify();
    }

    @Test
    @DisplayName("re-mints within the 60s refresh margin, before the token actually expires")
    void reMintsWithinRefreshMargin() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
                .andRespond(tokenResponse("token-old", 900));
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
                .andRespond(tokenResponse("token-new", 900));

        assertThat(provider.tokenFor(TENANT_A)).isEqualTo("token-old");
        // 850s in: 50s of life left — inside the 60s margin, so a fresh token is required.
        clock.advance(Duration.ofSeconds(850));
        assertThat(provider.tokenFor(TENANT_A)).isEqualTo("token-new");
        server.verify();
    }

    @Test
    @DisplayName("different tenants never share a cache entry")
    void tokensAreScopedPerTenant() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andRespond(tokenResponse("token-a", 900));
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
                .andExpect(jsonPath("$.tenantId").value(TENANT_B))
                .andRespond(tokenResponse("token-b", 900));

        assertThat(provider.tokenFor(TENANT_A)).isEqualTo("token-a");
        assertThat(provider.tokenFor(TENANT_B)).isEqualTo("token-b");
        server.verify();
    }

    @Test
    @DisplayName("currentTenantToken() reads the tenant from TenantContextHolder")
    void usesTenantContextHolder() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andRespond(tokenResponse("token-a", 900));
        TenantContextHolder.set(TENANT_A);

        assertThat(provider.currentTenantToken()).isEqualTo("token-a");
    }

    @Test
    @DisplayName("no tenant on the thread is an error — never a default")
    void requiresATenant() {
        assertThatThrownBy(() -> provider.currentTenantToken())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.tokenFor("  ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("401 from ACS fails closed")
    void failsClosedOnUnauthorized() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> provider.tokenFor(TENANT_A))
                .isInstanceOf(ServiceTokenUnavailableException.class);
    }

    @Test
    @DisplayName("ACS outage fails closed and is not cached")
    void failsClosedOnServerError() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI)).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
                .andRespond(tokenResponse("token-a", 900));

        assertThatThrownBy(() -> provider.tokenFor(TENANT_A))
                .isInstanceOf(ServiceTokenUnavailableException.class);
        // The failure left no poisoned cache entry: the next call retries and succeeds.
        assertThat(provider.tokenFor(TENANT_A)).isEqualTo("token-a");
        server.verify();
    }

    @Test
    @DisplayName("an empty accessToken fails closed rather than sending 'Bearer null'")
    void failsClosedOnEmptyToken() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(
                        withSuccess(
                                "{\"accessToken\":\"\",\"tokenType\":\"Bearer\",\"expiresIn\":900}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.tokenFor(TENANT_A))
                .isInstanceOf(ServiceTokenUnavailableException.class);
    }

    @Test
    @DisplayName("never leaks the secret or the token in an exception message")
    void neverLeaksCredentials() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> provider.tokenFor(TENANT_A))
                .isInstanceOf(ServiceTokenUnavailableException.class)
                .hasMessageNotContaining("dev-service-secret");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static org.springframework.test.web.client.ResponseCreator tokenResponse(
            String token, long expiresIn) {
        return withSuccess(
                String.format(
                        "{\"accessToken\":\"%s\",\"refreshToken\":null,\"tokenType\":\"Bearer\","
                                + "\"expiresIn\":%d,\"authorities\":[\"hist:write\"]}",
                        token, expiresIn),
                MediaType.APPLICATION_JSON);
    }

    /** Hand-advanced clock — the refresh margin is time-dependent behaviour, not a sleep. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
