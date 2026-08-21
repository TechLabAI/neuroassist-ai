package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.infrastructure.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration for {@link HistFactClient}.
 *
 * <p>Deliberately <b>not</b> annotated with {@code @Configuration}: this package is component
 * scanned, and a scanned {@code @Configuration} would make {@link HistServiceIdentityInterceptor}
 * global — i.e. every Feign client of pac-service would start calling as pac-service instead of as
 * the caregiver. Spring Cloud OpenFeign registers this class into the hist-service child context,
 * so the {@code @Bean} method is honoured without the annotation.
 */
public class HistFeignConfig {

    @Bean
    public RequestInterceptor histServiceIdentityInterceptor(
            ServiceTokenProvider serviceTokenProvider) {
        return new HistServiceIdentityInterceptor(serviceTokenProvider);
    }
}
