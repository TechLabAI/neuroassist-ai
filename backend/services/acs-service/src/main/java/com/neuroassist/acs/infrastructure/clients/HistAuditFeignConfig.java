package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.service.EdgeInternalTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configuração Feign por cliente para {@link HistAuditClient} (E2E-019).
 *
 * <p>Deliberadamente <b>sem</b> {@code @Configuration}, pela mesma razão de {@link
 * EdgeInternalFeignConfig}: este pacote é component scanned, e uma configuração escaneada tornaria
 * o interceptor global — o {@code PacPatientResolveClient} passaria a chamar como acs-service em
 * vez de como o cuidador, alargando em silêncio uma chamada cuja autorização depende de <i>quem
 * pediu</i>.
 *
 * <p>Diferente de {@link EdgeInternalFeignConfig} e {@link MigInternalFeignConfig} num ponto: o
 * token aqui é <b>escopado por tenant</b>. O hist-service roteia o {@code search_path} pelo claim,
 * então um fato de login com token sem tenant não chegaria a schema nenhum.
 */
public class HistAuditFeignConfig {

    @Bean
    public RequestInterceptor histAuditRequestInterceptor(EdgeInternalTokenProvider tokenProvider) {
        return new HistAuditRequestInterceptor(tokenProvider);
    }
}
