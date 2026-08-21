package com.neuroassist.edge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuroassist.edge.infrastructure.clients.ServiceIdentityRequestInterceptor;
import com.neuroassist.edge.infrastructure.config.FeignClientConfig;
import com.neuroassist.edge.infrastructure.security.ServiceTokenClient;
import com.neuroassist.edge.infrastructure.security.ServiceTokenProvider;
import com.neuroassist.edge.infrastructure.security.ServiceTokenResponse;
import com.neuroassist.edge.infrastructure.security.ServiceTokenUnavailableException;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for {@link ServiceIdentityRequestInterceptor}.
 *
 * <p>Proves the two traps of the service-identity contract §5 are handled: {@code header()} appends
 * (so the caller's header must be removed) and the interceptor must run <em>after</em> the global
 * {@code feignAuthPropagationInterceptor}.
 */
@DisplayName("ServiceIdentityRequestInterceptor — replaces the caller JWT with the service token")
class ServiceIdentityRequestInterceptorTest {

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final String CALLER_TOKEN = "caller-device-jwt";
    private static final String SERVICE_TOKEN = "service-identity-jwt";

    private final ServiceTokenClient fixedClient =
            tenantId -> new ServiceTokenResponse(SERVICE_TOKEN, "Bearer", 900);

    private final ServiceIdentityRequestInterceptor interceptor =
            new ServiceIdentityRequestInterceptor(new ServiceTokenProvider(fixedClient, 60));

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Caller Authorization is replaced, not appended — exactly one header value")
    void replacesCallerHeader() {
        TenantContextHolder.set(TENANT);
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer " + CALLER_TOKEN); // global interceptor did this

        interceptor.apply(template);

        Collection<String> values = template.headers().get("Authorization");
        assertThat(values).containsExactly("Bearer " + SERVICE_TOKEN);
        assertThat(values).noneMatch(v -> v.contains(CALLER_TOKEN));
    }

    @Test
    @DisplayName("Runs after the global propagation interceptor once the list is order-sorted")
    void orderedAfterGlobalPropagation() {
        TenantContextHolder.set(TENANT);
        Jwt jwt =
                Jwt.withTokenValue(CALLER_TOKEN)
                        .header("alg", "none")
                        .claim("sub", "device-d7b1")
                        .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        // Deliberately in the "wrong" declaration order — sorting must fix it, exactly as
        // FeignClientFactoryBean does with the injected List<RequestInterceptor>.
        List<RequestInterceptor> interceptors =
                new ArrayList<>(
                        List.of(
                                interceptor,
                                new FeignClientConfig().feignAuthPropagationInterceptor()));
        AnnotationAwareOrderComparator.sort(interceptors);

        assertThat(interceptors.get(interceptors.size() - 1)).isSameAs(interceptor);

        RequestTemplate template = new RequestTemplate();
        interceptors.forEach(i -> i.apply(template));

        assertThat(template.headers().get("Authorization"))
                .containsExactly("Bearer " + SERVICE_TOKEN);
    }

    @Test
    @DisplayName("Fail-closed: no service token means no request — never the caller's token")
    void failsClosedWhenTokenUnavailable() {
        ServiceTokenClient failing =
                tenantId -> {
                    throw new IllegalStateException("ACS down");
                };
        ServiceIdentityRequestInterceptor failClosed =
                new ServiceIdentityRequestInterceptor(new ServiceTokenProvider(failing, 60));
        TenantContextHolder.set(TENANT);
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer " + CALLER_TOKEN);

        assertThatThrownBy(() -> failClosed.apply(template))
                .isInstanceOf(ServiceTokenUnavailableException.class);
    }

    @Test
    @DisplayName("Fail-closed: no tenant in context aborts the call")
    void failsClosedWithoutTenant() {
        RequestTemplate template = new RequestTemplate();

        assertThatThrownBy(() -> interceptor.apply(template))
                .isInstanceOf(ServiceTokenUnavailableException.class);
    }
}
