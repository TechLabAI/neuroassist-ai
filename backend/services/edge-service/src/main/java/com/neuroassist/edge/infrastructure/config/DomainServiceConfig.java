package com.neuroassist.edge.infrastructure.config;

import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public DispositivoEdgeDomainService dispositivoEdgeDomainService() {
        return new DispositivoEdgeDomainService();
    }
}
