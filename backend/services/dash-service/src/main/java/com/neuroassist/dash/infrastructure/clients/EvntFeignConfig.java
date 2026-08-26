package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration for {@link EvntClient}.
 *
 * <p>Deliberately <b>not</b> annotated with {@code @Configuration}: this package is component
 * scanned, and a scanned {@code @Configuration} would make {@link EvntServiceIdentityInterceptor}
 * global — every Feign client of dash-service would then call as dash-service instead of as the
 * caregiver, which would break the patient-scoped timeline reads against hist-service. Spring Cloud
 * OpenFeign registers this class into the evnt-service child context, so the {@code @Bean} method
 * is honoured without the annotation.
 */
public class EvntFeignConfig {

    @Bean
    public RequestInterceptor evntServiceIdentityInterceptor(
            ServiceTokenProvider serviceTokenProvider) {
        return new EvntServiceIdentityInterceptor(serviceTokenProvider);
    }
}
