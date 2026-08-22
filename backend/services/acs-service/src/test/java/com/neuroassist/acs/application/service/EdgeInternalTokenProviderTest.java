package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EdgeInternalTokenProvider} — caching and fail-closed behaviour of the
 * {@code edge.internal} identity used by ACS→EDGE credential verification.
 */
class EdgeInternalTokenProviderTest {

    private static final String CLIENT_ID = "acs-service";
    private static final String CLIENT_SECRET = "dev-service-secret";

    private ServiceTokenService serviceTokenService;
    private MutableClock clock;
    private EdgeInternalTokenProvider provider;

    @BeforeEach
    void setUp() {
        serviceTokenService = mock(ServiceTokenService.class);
        clock = new MutableClock(Instant.parse("2026-08-22T10:00:00Z"));
        provider =
                new EdgeInternalTokenProvider(
                        serviceTokenService,
                        CLIENT_ID,
                        CLIENT_SECRET,
                        Duration.ofSeconds(60),
                        clock);
    }

    @Test
    @DisplayName("mints once and serves the cached token afterwards")
    void cachesToken() {
        when(serviceTokenService.issueTenantlessToken(CLIENT_ID, CLIENT_SECRET))
                .thenReturn(response("token-1", 900));

        assertThat(provider.tenantlessToken()).isEqualTo("token-1");
        assertThat(provider.tenantlessToken()).isEqualTo("token-1");

        verify(serviceTokenService, times(1)).issueTenantlessToken(CLIENT_ID, CLIENT_SECRET);
    }

    @Test
    @DisplayName("re-mints once the token is inside the refresh margin")
    void reMintsNearExpiry() {
        when(serviceTokenService.issueTenantlessToken(CLIENT_ID, CLIENT_SECRET))
                .thenReturn(response("token-1", 900), response("token-2", 900));

        assertThat(provider.tenantlessToken()).isEqualTo("token-1");
        clock.advance(Duration.ofSeconds(850)); // 50s left < 60s margin
        assertThat(provider.tenantlessToken()).isEqualTo("token-2");

        verify(serviceTokenService, times(2)).issueTenantlessToken(CLIENT_ID, CLIENT_SECRET);
    }

    @Test
    @DisplayName("asks for a tenant-less token — the device's tenant is not known yet")
    void asksForTenantlessToken() {
        when(serviceTokenService.issueTenantlessToken(CLIENT_ID, CLIENT_SECRET))
                .thenReturn(response("token-1", 900));

        provider.tenantlessToken();

        // The only token method the provider is allowed to use: no tenant is available at this
        // point in the flow, and defaulting one would be a silent cross-tenant hazard.
        verify(serviceTokenService).issueTenantlessToken(CLIENT_ID, CLIENT_SECRET);
    }

    @Test
    @DisplayName("fail-closed: a rejected client secret propagates instead of returning null")
    void failsClosedOnRejection() {
        when(serviceTokenService.issueTenantlessToken(CLIENT_ID, CLIENT_SECRET))
                .thenThrow(new InvalidCredentialsException());

        assertThatThrownBy(() -> provider.tenantlessToken())
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("evictAll forces the next call to mint again")
    void evictAllDropsCache() {
        when(serviceTokenService.issueTenantlessToken(CLIENT_ID, CLIENT_SECRET))
                .thenReturn(response("token-1", 900), response("token-2", 900));

        assertThat(provider.tenantlessToken()).isEqualTo("token-1");
        provider.evictAll();
        assertThat(provider.tenantlessToken()).isEqualTo("token-2");
    }

    private static AutenticacaoResponse response(String token, long expiresIn) {
        return new AutenticacaoResponse(
                token, null, "Bearer", expiresIn, null, null, null, List.of("edge.internal"));
    }

    /** Minimal mutable clock — avoids a sleep in the expiry tests. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
