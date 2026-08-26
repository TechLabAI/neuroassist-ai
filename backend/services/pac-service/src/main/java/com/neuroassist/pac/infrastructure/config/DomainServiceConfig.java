package com.neuroassist.pac.infrastructure.config;

import com.neuroassist.pac.domain.service.PacienteDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serviços de domínio são POJOs (RULE-01) e precisam ser registrados como {@code @Bean}.
 *
 * <p>{@code TipoGestoRefDomainService} saiu daqui em E2E-024: era uma classe vazia com {@code //
 * TODO: implement domain invariants}, registrada no contexto do Spring e injetada por ninguém. Um
 * bean vazio no contexto não quebra nada — apenas afirma uma capacidade que não existe.
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public PacienteDomainService pacienteDomainService() {
        return new PacienteDomainService();
    }
}
