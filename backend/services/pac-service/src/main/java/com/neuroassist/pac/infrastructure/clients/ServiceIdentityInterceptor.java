package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.infrastructure.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Replaces the caller's bearer token with pac-service's own service-identity token (see {@code
 * docs/architecture/backend/standards/service-identity.md} §5).
 *
 * <p>Wired per client, never globally: into {@link HistFactClient} via {@link HistFeignConfig}, and
 * into {@code AcsLinkClient} via {@code AcsLinkFeignConfig}. {@code AcsDirectoryClient}, {@code
 * GestDetectabilityClient} and {@code EdgeDeviceClient} keep propagating the caller's token,
 * because those authorizations depend on <i>who asked</i>.
 *
 * <p>The criterion is that sentence: writing an audit fact does not depend on who asked, and
 * neither does propagating a caregiver-patient link from a scheduled sweep — there is no caller
 * there at all. Both are pac-service acting on its own behalf.
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
 *       are asserted by {@code ServiceIdentityInterceptorTest}.
 * </ol>
 *
 * <p><b>Fail-closed.</b> No tenant on the thread, or ACS unable to mint a token, throws. Feign
 * surfaces that as a failed call, which {@code HistFactAdapter} absorbs by leaving the fact {@code
 * pending} in the outbox for retry. There is deliberately no fallback to the caller's token.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ServiceIdentityInterceptor implements RequestInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private final ServiceTokenProvider serviceTokenProvider;

    @Override
    public void apply(RequestTemplate template) {
        String serviceToken = serviceTokenProvider.currentTenantToken();
        template.removeHeader(AUTH_HEADER);
        template.header(AUTH_HEADER, "Bearer " + serviceToken);
    }
}
