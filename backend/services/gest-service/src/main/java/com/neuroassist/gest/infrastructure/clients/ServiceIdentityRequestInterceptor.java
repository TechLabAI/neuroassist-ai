package com.neuroassist.gest.infrastructure.clients;

import com.neuroassist.gest.infrastructure.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

/**
 * Troca o JWT propagado do chamador pelo token de <b>identidade de serviço</b> do GEST, nos
 * clientes Feign que o carregam (service-identity.md §5).
 *
 * <p>Por que trocar: quem chama {@code GET /detection-profile/devices/&#123;id&#125;} é o EDGE,
 * com um token de serviço que carrega apenas {@code gest.detection-profile.read}. Propagá-lo ao
 * PAC dá 403 — e um 403 dentro de um circuit breaker vira "serviço degradado", que foi exatamente
 * o defeito C-19.
 *
 * <p>Duas armadilhas, as mesmas que o EDGE documenta:
 *
 * <ol>
 *   <li>{@link RequestTemplate#header} <b>acrescenta</b>. O interceptor global de propagação já
 *       colocou o {@code Authorization} do chamador, então ele tem de ser removido antes — senão a
 *       requisição leva dois valores e o servidor lê o primeiro.
 *   <li>Ordem. Implementa {@link Ordered} com {@link Ordered#LOWEST_PRECEDENCE} para que o
 *       comparador do Spring Cloud sempre o coloque depois do interceptor global. {@code @Order}
 *       num {@code @Bean} que devolve lambda seria invisível a esse comparador — daí uma classe de
 *       verdade.
 * </ol>
 *
 * <p><b>Falha fechada:</b> se o token não puder ser obtido a exceção propaga e a requisição não é
 * enviada. Nunca cai de volta no token do chamador.
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
        log.debug("Identidade de servico aplicada a {} {}", template.method(), template.path());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
