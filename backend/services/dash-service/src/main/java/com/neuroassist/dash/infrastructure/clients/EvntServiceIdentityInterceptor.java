package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Replaces the caller's bearer token with dash-service's own service-identity token on calls to
 * evnt-service (standards/service-identity.md §5).
 *
 * <p><b>Why evnt-service and not the others.</b> {@code HistClient} and {@code EdgeClient} answer
 * questions whose authorization depends on <i>who asked</i> — a caregiver may only read the
 * timeline of their own patients — so those keep propagating the caller's JWT. The evnt-service
 * calls are different in kind: three of the six ({@code reportDelivered}, {@code reportNoSession},
 * {@code reportAcknowledgedCallback}) are dash-service reporting its own delivery outcome, and two
 * of them fire inside the {@code POST /internal/notifications} request, whose SecurityContext holds
 * <i>evnt-service's</i> token — propagating it would send evnt-service's own token back to
 * evnt-service. The remaining three carry the caregiver explicitly as a {@code caregiverId}
 * parameter, and dash-service has already authorised that person at its {@code /panel/**} boundary.
 *
 * <p><b>Two traps this class exists to avoid</b> (both learned in pac-service):
 *
 * <ol>
 *   <li>{@link RequestTemplate#header(String, String...)} <i>appends</i> — the global {@code
 *       feignAuthPropagationInterceptor} may already have put a token there, so the header must be
 *       removed first or the request carries both.
 *   <li>Removal only helps if this interceptor runs <b>after</b> the global one. Spring Cloud
 *       OpenFeign sorts interceptors with {@code AnnotationAwareOrderComparator}, so this class is
 *       {@link Ordered#LOWEST_PRECEDENCE} while the global one is pinned to a higher precedence.
 * </ol>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class EvntServiceIdentityInterceptor implements RequestInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private final ServiceTokenProvider serviceTokenProvider;

    @Override
    public void apply(RequestTemplate template) {
        String serviceToken = serviceTokenProvider.currentTenantToken();
        template.removeHeader(AUTH_HEADER);
        template.header(AUTH_HEADER, "Bearer " + serviceToken);
    }
}
