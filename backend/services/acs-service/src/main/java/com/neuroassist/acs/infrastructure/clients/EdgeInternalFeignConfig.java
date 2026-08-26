package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.service.EdgeInternalTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration for {@link EdgeDeviceCredentialClient}.
 *
 * <p>Deliberately <b>not</b> annotated with {@code @Configuration}: this package is component
 * scanned, and a scanned {@code @Configuration} would make {@link
 * InternalServiceIdentityInterceptor} global — every Feign client of acs-service would then call as
 * acs-service instead of as the caregiver, which is wrong for {@code PacPatientResolveClient} (that
 * authorisation depends on <i>who asked</i>). Spring Cloud OpenFeign registers this class into the
 * edge-service child context, so the {@code @Bean} method is honoured without the annotation.
 */
public class EdgeInternalFeignConfig {

    @Bean
    public RequestInterceptor edgeInternalRequestInterceptor(
            EdgeInternalTokenProvider tokenProvider) {
        return new InternalServiceIdentityInterceptor(tokenProvider);
    }
}
