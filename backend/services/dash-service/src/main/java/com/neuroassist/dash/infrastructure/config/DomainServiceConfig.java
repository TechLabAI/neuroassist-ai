package com.neuroassist.dash.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Registra domain services como @Bean. Domain services são POJOs puros (sem @Service) e precisam
 * ser declarados aqui. Adicionar um @Bean por domain service ao implementar o domínio.
 */
@Configuration
public class DomainServiceConfig {
    // TODO: registrar domain services à medida que forem implementados.
    // Exemplo:
    //   @Bean
    //   public AssessmentDomainService assessmentDomainService() {
    //       return new AssessmentDomainService();
    //   }
}
