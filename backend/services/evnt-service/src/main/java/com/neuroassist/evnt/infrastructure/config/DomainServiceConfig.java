package com.neuroassist.evnt.infrastructure.config;

import com.neuroassist.evnt.domain.service.AlarmRelevancePolicy;
import com.neuroassist.evnt.domain.service.EventoDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Serviços de domínio são POJOs (RULE-01) e precisam ser declarados como {@code @Bean} aqui. */
@Configuration
public class DomainServiceConfig {

    @Bean
    public EventoDomainService eventoDomainService() {
        return new EventoDomainService();
    }

    @Bean
    public AlarmRelevancePolicy alarmRelevancePolicy(AlarmExpiryProperties properties) {
        return new AlarmRelevancePolicy(properties.getTtl(), properties.getHelpTerms());
    }
}
