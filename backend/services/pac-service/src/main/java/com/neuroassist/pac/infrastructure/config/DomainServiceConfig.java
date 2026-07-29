package com.neuroassist.pac.infrastructure.config;

import com.neuroassist.pac.domain.service.PacienteDomainService;
import com.neuroassist.pac.domain.service.TipoGestoRefDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public PacienteDomainService pacienteDomainService() {
        return new PacienteDomainService();
    }

    @Bean
    public TipoGestoRefDomainService tipoGestoRefDomainService() {
        return new TipoGestoRefDomainService();
    }
}
