package com.neuroassist.hist.infrastructure.config;

import com.neuroassist.hist.domain.service.ActorRefDomainService;
import com.neuroassist.hist.domain.service.AuditRecordDomainService;
import com.neuroassist.hist.domain.service.FactTypeDomainService;
import com.neuroassist.hist.domain.service.HashChainDomainService;
import com.neuroassist.hist.domain.service.LgpdPayloadGuard;
import com.neuroassist.hist.domain.service.SubjectRefDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public AuditRecordDomainService auditRecordDomainService() {
        return new AuditRecordDomainService();
    }

    @Bean
    public SubjectRefDomainService subjectRefDomainService() {
        return new SubjectRefDomainService();
    }

    @Bean
    public ActorRefDomainService actorRefDomainService() {
        return new ActorRefDomainService();
    }

    @Bean
    public HashChainDomainService hashChainDomainService() {
        return new HashChainDomainService();
    }

    @Bean
    public FactTypeDomainService factTypeDomainService() {
        return new FactTypeDomainService();
    }

    @Bean
    public LgpdPayloadGuard lgpdPayloadGuard() {
        return new LgpdPayloadGuard();
    }
}
