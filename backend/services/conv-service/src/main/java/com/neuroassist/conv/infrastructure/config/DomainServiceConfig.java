package com.neuroassist.conv.infrastructure.config;

import com.neuroassist.conv.domain.service.ConvArvoreFalaDomainService;
import com.neuroassist.conv.domain.service.ConvAuditOutboxDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public ConvArvoreFalaDomainService convArvoreFalaDomainService() {
        return new ConvArvoreFalaDomainService();
    }

    @Bean
    public ConvAuditOutboxDomainService convAuditOutboxDomainService() {
        return new ConvAuditOutboxDomainService();
    }
}
