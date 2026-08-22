package com.neuroassist.acs.infrastructure.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.application.service.EdgeInternalTokenProvider;
import com.neuroassist.acs.infrastructure.config.FeignClientConfig;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
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
 * Service identity for ACS → EDGE (standards/service-identity.md §5, §7).
 *
 * <p>The trap being guarded: {@code RequestTemplate.header()} appends, and the global {@code
 * CallerTokenPropagationInterceptor} may already have written a caller token. These tests assert
 * the final header is the service token <b>and only</b> the service token, with the interceptors
 * ordered exactly as Spring Cloud OpenFeign orders them — which is why neither is a lambda.
 */
class EdgeInternalRequestInterceptorTest {

    private static final String CALLER_TOKEN = "caregiver.jwt.value";
    private static final String SERVICE_TOKEN = "acs.internal.jwt.value";

    private EdgeInternalTokenProvider provider;
    private EdgeInternalRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        provider = mock(EdgeInternalTokenProvider.class);
        interceptor = new EdgeInternalRequestInterceptor(provider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Header replacement")
    class HeaderReplacement {

        @Test
        @DisplayName("replaces a caller Authorization header instead of appending to it")
        void replacesCallerAuthorizationHeader() {
            when(provider.tenantlessToken()).thenReturn(SERVICE_TOKEN);
            RequestTemplate template = new RequestTemplate();
            template.header("Authorization", "Bearer " + CALLER_TOKEN);

            interceptor.apply(template);

            assertThat(template.headers().get("Authorization"))
                    .containsExactly("Bearer " + SERVICE_TOKEN);
        }

        @Test
        @DisplayName("sets the service token when no Authorization header is present")
        void setsHeaderWhenAbsent() {
            when(provider.tenantlessToken()).thenReturn(SERVICE_TOKEN);
            RequestTemplate template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers().get("Authorization"))
                    .containsExactly("Bearer " + SERVICE_TOKEN);
        }

        @Test
        @DisplayName("leaves every other header untouched")
        void preservesOtherHeaders() {
            when(provider.tenantlessToken()).thenReturn(SERVICE_TOKEN);
            RequestTemplate template = new RequestTemplate();
            template.header("X-Correlation-Id", "corr-1");

            interceptor.apply(template);

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
         * chance, the caller token would win.
         */
        @Test
        @DisplayName("service identity runs last, so a caller token never survives")
        void serviceIdentityRunsAfterCallerPropagation() {
            when(provider.tenantlessToken()).thenReturn(SERVICE_TOKEN);
            SecurityContextHolder.getContext().setAuthentication(callerAuthentication());

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
                    .noneMatch(value -> value.contains(CALLER_TOKEN));
        }

        @Test
        @DisplayName("caller propagation alone still forwards the caller token (other clients)")
        void callerPropagationUnchangedForOtherClients() {
            SecurityContextHolder.getContext().setAuthentication(callerAuthentication());
            RequestTemplate template = new RequestTemplate();

            new FeignClientConfig.CallerTokenPropagationInterceptor().apply(template);

            assertThat(template.headers().get("Authorization"))
                    .containsExactly("Bearer " + CALLER_TOKEN);
        }
    }

    @Nested
    @DisplayName("Fail-closed")
    class FailClosed {

        @Test
        @DisplayName("propagates the token failure instead of calling EDGE unauthenticated")
        void propagatesTokenFailure() {
            when(provider.tenantlessToken()).thenThrow(new InvalidCredentialsException());
            RequestTemplate template = new RequestTemplate();
            template.header("Authorization", "Bearer " + CALLER_TOKEN);

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    private static JwtAuthenticationToken callerAuthentication() {
        Jwt jwt =
                new Jwt(
                        CALLER_TOKEN,
                        Instant.now(),
                        Instant.now().plusSeconds(600),
                        Map.of("alg", "RS256"),
                        Map.of("sub", "11111111-1111-1111-1111-111111111111"));
        return new JwtAuthenticationToken(jwt);
    }
}
