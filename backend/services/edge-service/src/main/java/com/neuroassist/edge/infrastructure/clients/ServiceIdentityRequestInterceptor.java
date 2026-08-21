package com.neuroassist.edge.infrastructure.clients;

import com.neuroassist.edge.infrastructure.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

/**
 * Replaces the caller's propagated JWT with the EDGE <b>service-identity</b> token on the Feign
 * clients that carry it (service-identity contract §5).
 *
 * <p>Why replace: the heartbeat is authenticated with a device token, which never holds {@code
 * gest.detection-profile.read}; propagating it made GEST answer 403 and the circuit breaker
 * mistranslate that into "degraded" (defect C-19).
 *
 * <p>Two traps handled here:
 *
 * <ol>
 *   <li>{@link RequestTemplate#header} <b>appends</b>. {@code feignAuthPropagationInterceptor} has
 *       already added the caller's {@code Authorization}, so it must be removed first — otherwise
 *       the request carries two values and the server picks the first.
 *   <li>Order. This interceptor implements {@link Ordered} with {@link Ordered#LOWEST_PRECEDENCE}
 *       so Spring Cloud's {@code AnnotationAwareOrderComparator.sort} on the injected {@code
 *       List<RequestInterceptor>} always places it after the global propagation one (which declares
 *       a strictly lower order). Note that {@code @Order} on a {@code @Bean} method that returns a
 *       lambda would be invisible to that comparator — hence a real class implementing {@code
 *       Ordered}.
 * </ol>
 *
 * <p><b>Fail-closed:</b> if the service token cannot be obtained the exception propagates and the
 * request is never sent. It never falls back to the caller's token.
 */
@Slf4j
@RequiredArgsConstructor
public class ServiceIdentityRequestInterceptor implements RequestInterceptor, Ordered {

    static final String AUTH_HEADER = "Authorization";

    private final ServiceTokenProvider serviceTokenProvider;

    @Override
    public void apply(RequestTemplate template) {
        String token = serviceTokenProvider.tokenForCurrentTenant();
        template.removeHeader(AUTH_HEADER);
        template.header(AUTH_HEADER, "Bearer " + token);
        log.debug("Service identity applied to {} {}", template.method(), template.path());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
