package com.neuroassist.acs.infrastructure.config;

import com.neuroassist.acs.domain.service.CuidadorDomainService;
import com.neuroassist.acs.domain.service.SigningKeyDomainService;
import com.neuroassist.acs.domain.service.TenantDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public TenantDomainService tenantDomainService() {
        return new TenantDomainService();
    }

    @Bean
    public SigningKeyDomainService signingKeyDomainService() {
        return new SigningKeyDomainService();
    }

    @Bean
    public CuidadorDomainService cuidadorDomainService() {
        return new CuidadorDomainService();
    }
}
