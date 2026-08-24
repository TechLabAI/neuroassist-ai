package com.neuroassist.gest.infrastructure.config;

import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.core.Ordered;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Feign interceptors — propagate tenantId and JWT bearer token to downstream services (RULE-06:
 * only in infrastructure.clients/config).
 *
 * <p>X-Correlation-Id propagation is handled by {@link
 * com.neuroassist.gest.infrastructure.clients.CorrelationIdFeignInterceptor} (a {@code @Component}
 * in {@code infrastructure.clients}), auto-discovered by Spring Cloud OpenFeign.
 */
@Configuration
public class FeignClientConfig {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String AUTH_HEADER = "Authorization";

    @Bean
    public RequestInterceptor feignTenantIdInterceptor() {
        return (RequestTemplate template) -> {
            String tenantId = TenantContextHolder.getTenantIdOrNull();
            if (tenantId != null && !tenantId.isBlank()) {
                template.header(TENANT_HEADER, tenantId);
            }
        };
    }

    /**
     * Propaga o JWT do chamador nos clientes Feign que não usam identidade de serviço.
     *
     * <p><b>Classe {@link Ordered} nomeada, e não lambda</b> (E2E-034). O Spring Cloud ordena a
     * {@code List<RequestInterceptor>} injetada com {@code AnnotationAwareOrderComparator}, que lê
     * a ordem da <em>instância</em>: um {@code @Order} num {@code @Bean} que devolve lambda é
     * invisível para ele. Sem ordem explícita, este interceptor empatava em {@code
     * LOWEST_PRECEDENCE} com o {@link
     * com.neuroassist.gest.infrastructure.clients.ServiceIdentityRequestInterceptor} — e, quando
     * rodava depois, <b>reanexava</b> o token do chamador ao header que o outro acabara de
     * substituir ({@code RequestTemplate.header} acrescenta, não sobrescreve). A requisição saía
     * com dois {@code Authorization} e o servidor lia o primeiro: o do chamador, sem a authority
     * necessária. Resultado observado: 403 do EDGE, traduzido pelo breaker em "EDGE indisponível".
     */
    @Bean
    public RequestInterceptor feignAuthPropagationInterceptor() {
        return new AuthPropagationInterceptor();
    }

    /** Estritamente antes de {@code LOWEST_PRECEDENCE}, onde vive o interceptor de identidade. */
    static final class AuthPropagationInterceptor implements RequestInterceptor, Ordered {

        static final int ORDER = Ordered.LOWEST_PRECEDENCE - 1000;

        @Override
        public void apply(RequestTemplate template) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                if (jwt != null && jwt.getTokenValue() != null) {
                    template.header(AUTH_HEADER, "Bearer " + jwt.getTokenValue());
                }
            }
        }

        @Override
        public int getOrder() {
            return ORDER;
        }
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
