package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.infrastructure.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Replaces the caller's bearer token with pac-service's own service-identity token on calls to
 * hist-service (see {@code docs/architecture/backend/standards/service-identity.md} §5).
 *
 * <p>Only wired into {@link HistFactClient} via {@link HistFeignConfig} — {@code
 * AcsDirectoryClient}, {@code GestDetectabilityClient} and {@code EdgeDeviceClient} keep
 * propagating the caller's token, because those authorizations depend on <i>who asked</i>. Writing
 * an audit fact does not: it is pac-service acting on its own behalf.
 *
 * <p><b>Two traps this class exists to avoid.</b>
 *
 * <ol>
 *   <li>{@link RequestTemplate#header(String, String...)} <i>appends</i>. The global {@code
 *       feignAuthPropagationInterceptor} already put the caregiver's token there, so the header
 *       must be removed first — otherwise the request carries both.
 *   <li>Removal only helps if this interceptor runs <b>after</b> the global one. Spring Cloud
 *       OpenFeign sorts the injected {@code List<RequestInterceptor>} with {@code
 *       AnnotationAwareOrderComparator}, so this class is {@link Ordered#LOWEST_PRECEDENCE} while
 *       {@code CallerTokenPropagationInterceptor} pins itself to a higher precedence. Both orders
 *       are asserted by {@code HistServiceIdentityInterceptorTest}.
 * </ol>
 *
 * <p><b>Fail-closed.</b> No tenant on the thread, or ACS unable to mint a token, throws. Feign
 * surfaces that as a failed call, which {@code HistFactAdapter} absorbs by leaving the fact {@code
 * pending} in the outbox for retry. There is deliberately no fallback to the caller's token.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class HistServiceIdentityInterceptor implements RequestInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private final ServiceTokenProvider serviceTokenProvider;

    @Override
    public void apply(RequestTemplate template) {
        String serviceToken = serviceTokenProvider.currentTenantToken();
        template.removeHeader(AUTH_HEADER);
        template.header(AUTH_HEADER, "Bearer " + serviceToken);
    }
}
