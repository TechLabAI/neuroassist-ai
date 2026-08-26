package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.service.EdgeInternalTokenProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Anexa o token de serviço do acs-service, escopado ao tenant do fato (E2E-019).
 *
 * <p><b>O tenant não vem de um contexto de thread.</b> Um fato de autenticação é gravado durante o
 * login — antes de existir qualquer contexto de tenant na requisição, porque descobrir de quem é o
 * login é justamente o que o login faz. O tenant é passado explicitamente por {@link
 * #forTenant(UUID)} imediatamente antes da chamada.
 *
 * <p>Duas armadilhas que esta classe existe para evitar, as mesmas do interceptor do PAC:
 *
 * <ol>
 *   <li>{@link RequestTemplate#header(String, String...)} <b>acrescenta</b>. Sem remover antes, a
 *       requisição sairia com dois {@code Authorization}.
 *   <li>Remover só ajuda se este interceptor rodar <b>depois</b> do global. Daí {@link
 *       Ordered#LOWEST_PRECEDENCE}.
 * </ol>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class HistAuditRequestInterceptor implements RequestInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    /**
     * Tenant do fato em vôo. {@code ThreadLocal} porque o Feign não oferece caminho para passar
     * contexto por chamada, e a alternativa — um campo mutável no interceptor, que é singleton por
     * cliente — corromperia chamadas concorrentes de tenants diferentes.
     */
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private final EdgeInternalTokenProvider tokenProvider;

    /** Executa {@code call} com o tenant vinculado a esta thread, limpando ao final. */
    public static <T> T forTenant(UUID tenantId, java.util.function.Supplier<T> call) {
        CURRENT_TENANT.set(tenantId);
        try {
            return call.get();
        } finally {
            CURRENT_TENANT.remove();
        }
    }

    @Override
    public void apply(RequestTemplate template) {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            // Falha fechada: sem tenant o hist-service gravaria no schema errado ou em nenhum.
            // Melhor a chamada falhar e o fato ficar registrado como não entregue.
            throw new IllegalStateException(
                    "audit fact sent without a bound tenant — refusing to guess");
        }
        template.removeHeader(AUTH_HEADER);
        template.header(AUTH_HEADER, "Bearer " + tokenProvider.tokenForTenant(tenantId));
    }
}
