package com.neuroassist.acs.infrastructure.config;

import com.neuroassist.acs.infrastructure.security.MultiTenantConnectionProvider;
import com.neuroassist.acs.infrastructure.security.TenantIdentifierResolver;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Hibernate multi-tenancy beans into Spring's JPA auto-config (D-071). Strategy comes from
 * multi-tenancy.arch.json isolation_strategies[?id=schema-per-tenant].hibernate_strategy (D-063).
 */
@Configuration
@RequiredArgsConstructor
public class HibernateMultiTenancyConfig {

    private final MultiTenantConnectionProvider connectionProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;

    @Bean
    public HibernatePropertiesCustomizer multiTenancyCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            hibernateProperties.put(
                    AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
