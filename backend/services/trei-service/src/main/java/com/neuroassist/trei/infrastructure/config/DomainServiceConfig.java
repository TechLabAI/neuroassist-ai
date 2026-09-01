package com.neuroassist.trei.infrastructure.config;

import com.neuroassist.trei.domain.service.TreiSessaoTreinamentoDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public TreiSessaoTreinamentoDomainService treiSessaoTreinamentoDomainService() {
        return new TreiSessaoTreinamentoDomainService();
    }
}
