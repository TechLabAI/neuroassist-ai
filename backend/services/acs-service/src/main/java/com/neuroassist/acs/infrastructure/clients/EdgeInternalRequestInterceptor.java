package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.service.EdgeInternalTokenProvider;
import com.neuroassist.acs.infrastructure.config.FeignClientConfig;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Puts acs-service's own {@code edge.internal} service token on calls to {@link
 * EdgeDeviceCredentialClient} ({@code standards/service-identity.md} §5).
 *
 * <p><b>Two traps this class exists to avoid.</b>
 *
 * <ol>
 *   <li>{@link RequestTemplate#header(String, String...)} <i>appends</i>. The global {@code
 *       feignAuthPropagationInterceptor} may already have written a caller token there, so it must
 *       be removed first — otherwise the request carries both and edge-service authorises against
 *       whichever it reads.
 *   <li>Removal only helps if this interceptor runs <b>after</b> the global one. Spring Cloud
 *       OpenFeign sorts the injected {@code List<RequestInterceptor>} with {@code
 *       AnnotationAwareOrderComparator}, which reads the order from the <i>instance</i> — so both
 *       interceptors are real classes carrying {@code @Order}, never lambdas. This one is {@link
 *       Ordered#LOWEST_PRECEDENCE}; {@code CallerTokenPropagationInterceptor} pins itself to {@link
 *       FeignClientConfig#CALLER_TOKEN_PROPAGATION_ORDER}. Both are asserted by {@code
 *       EdgeInternalRequestInterceptorTest}.
 * </ol>
 *
 * <p><b>Fail-closed.</b> If the token cannot be minted the interceptor throws, Feign surfaces a
 * failed call, and the device token request becomes a generic 401. There is deliberately no
 * fallback to an unauthenticated call or to a caller token.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class EdgeInternalRequestInterceptor implements RequestInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private final EdgeInternalTokenProvider tokenProvider;

    @Override
    public void apply(RequestTemplate template) {
        String serviceToken = tokenProvider.tenantlessToken();
        template.removeHeader(AUTH_HEADER);
        template.header(AUTH_HEADER, "Bearer " + serviceToken);
    }
}
