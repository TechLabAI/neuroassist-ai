package com.neuroassist.gest.infrastructure.config;

import com.neuroassist.gest.domain.service.DetectionProfileDomainService;
import com.neuroassist.gest.domain.service.TipoGestoDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public TipoGestoDomainService tipoGestoDomainService() {
        return new TipoGestoDomainService();
    }

    @Bean
    public DetectionProfileDomainService detectionProfileDomainService() {
        return new DetectionProfileDomainService();
    }
}
