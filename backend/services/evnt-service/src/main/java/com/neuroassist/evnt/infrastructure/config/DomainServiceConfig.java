package com.neuroassist.evnt.infrastructure.config;

import com.neuroassist.evnt.domain.service.EventoDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public EventoDomainService eventoDomainService() {
        return new EventoDomainService();
    }
}
