package com.neuroassist.pac.infrastructure.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neuroassist.pac.infrastructure.config.FeignClientConfig;
import com.neuroassist.pac.infrastructure.security.ServiceTokenProvider;
import com.neuroassist.pac.infrastructure.security.ServiceTokenUnavailableException;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Service identity for PAC → HIST (standards/service-identity.md §5).
 *
 * <p>The trap being guarded: {@code RequestTemplate.header()} appends, and the global {@code
 * CallerTokenPropagationInterceptor} already wrote the caregiver's token. These tests assert the
 * final header is the service token <b>and only</b> the service token, with the interceptors
 * ordered exactly as Spring Cloud OpenFeign orders them.
 */
class HistServiceIdentityInterceptorTest {

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final String CAREGIVER_TOKEN = "caregiver.jwt.value";
    private static final String SERVICE_TOKEN = "service.jwt.value";

    private ServiceTokenProvider provider;
    private HistServiceIdentityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        provider = mock(ServiceTokenProvider.class);
        interceptor = new HistServiceIdentityInterceptor(provider);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Header replacement")
    class HeaderReplacement {

        @Test
        @DisplayName("replaces the caller's Authorization header instead of appending to it")
        void replacesCallerAuthorizationHeader() {
            when(provider.currentTenantToken()).thenReturn(SERVICE_TOKEN);
            RequestTemplate template = new RequestTemplate();
            template.header("Authorization", "Bearer " + CAREGIVER_TOKEN);

            interceptor.apply(template);

            assertThat(template.headers().get("Authorization"))
                    .containsExactly("Bearer " + SERVICE_TOKEN);
        }

        @Test
        @DisplayName("sets the service token when no Authorization header is present")
        void setsHeaderWhenAbsent() {
            when(provider.currentTenantToken()).thenReturn(SERVICE_TOKEN);
            RequestTemplate template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers().get("Authorization"))
                    .containsExactly("Bearer " + SERVICE_TOKEN);
        }

        @Test
        @DisplayName("leaves every other header untouched")
        void preservesOtherHeaders() {
            when(provider.currentTenantToken()).thenReturn(SERVICE_TOKEN);
            RequestTemplate template = new RequestTemplate();
            template.header("X-Tenant-Id", TENANT);
            template.header("X-Correlation-Id", "corr-1");

            interceptor.apply(template);

            assertThat(template.headers().get("X-Tenant-Id")).containsExactly(TENANT);
            assertThat(template.headers().get("X-Correlation-Id")).containsExactly("corr-1");
        }
    }

    @Nested
    @DisplayName("Interceptor ordering (the real Feign pipeline)")
    class Ordering {

        /**
         * Reproduces what {@code FeignClientFactoryBean} does: collect every {@code
         * RequestInterceptor} bean, sort with {@link AnnotationAwareOrderComparator}, apply in
         * order. Registration order is deliberately reversed here — if ordering were left to
         * chance, the caregiver token would win.
         */
        @Test
        @DisplayName("service identity runs last, so the caregiver token never survives")
        void serviceIdentityRunsAfterCallerPropagation() {
            when(provider.currentTenantToken()).thenReturn(SERVICE_TOKEN);
            SecurityContextHolder.getContext().setAuthentication(caregiverAuthentication());

            List<RequestInterceptor> interceptors =
                    new ArrayList<>(
                            List.of(
                                    interceptor,
                                    new FeignClientConfig.CallerTokenPropagationInterceptor()));
            AnnotationAwareOrderComparator.sort(interceptors);

            assertThat(interceptors.get(0))
                    .isInstanceOf(FeignClientConfig.CallerTokenPropagationInterceptor.class);
            assertThat(interceptors.get(1)).isSameAs(interceptor);

            RequestTemplate template = new RequestTemplate();
            interceptors.forEach(i -> i.apply(template));

            assertThat(template.headers().get("Authorization"))
                    .containsExactly("Bearer " + SERVICE_TOKEN);
            assertThat(template.headers().get("Authorization"))
                    .noneMatch(value -> value.contains(CAREGIVER_TOKEN));
        }

        @Test
        @DisplayName("caller propagation alone still forwards the caregiver token (other clients)")
        void callerPropagationUnchangedForOtherClients() {
            SecurityContextHolder.getContext().setAuthentication(caregiverAuthentication());
            RequestTemplate template = new RequestTemplate();

            new FeignClientConfig.CallerTokenPropagationInterceptor().apply(template);

            assertThat(template.headers().get("Authorization"))
                    .containsExactly("Bearer " + CAREGIVER_TOKEN);
        }
    }

    @Nested
    @DisplayName("Fail-closed")
    class FailClosed {

        @Test
        @DisplayName("propagates the token failure instead of falling back to the caller's token")
        void propagatesTokenFailure() {
            when(provider.currentTenantToken())
                    .thenThrow(new ServiceTokenUnavailableException("acs down"));
            RequestTemplate template = new RequestTemplate();
            template.header("Authorization", "Bearer " + CAREGIVER_TOKEN);

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(ServiceTokenUnavailableException.class);
        }

        @Test
        @DisplayName("refuses to call without a tenant on the thread")
        void refusesWithoutTenant() {
            TenantContextHolder.clear();
            when(provider.currentTenantToken())
                    .thenThrow(new IllegalStateException("TenantContext not set"));

            assertThatThrownBy(() -> interceptor.apply(new RequestTemplate()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private static JwtAuthenticationToken caregiverAuthentication() {
        Jwt jwt =
                new Jwt(
                        CAREGIVER_TOKEN,
                        Instant.now(),
                        Instant.now().plusSeconds(600),
                        Map.of("alg", "RS256"),
                        Map.of("sub", "11111111-1111-1111-1111-111111111111", "empresaId", TENANT));
        return new JwtAuthenticationToken(jwt);
    }
}
