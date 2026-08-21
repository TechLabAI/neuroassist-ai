package com.neuroassist.edge.infrastructure.clients;

import com.neuroassist.edge.infrastructure.security.ServiceTokenProvider;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration for {@link DetectionProfileClient} (EDGE → GEST).
 *
 * <p>Intentionally <b>not</b> annotated with {@code @Configuration}: Spring Cloud registers it in
 * the Feign client's child context via {@code @FeignClient(configuration = ...)}, so component
 * scanning must not pick it up — otherwise the service-identity interceptor would become global and
 * hijack the {@code Authorization} header of every other Feign client.
 */
public class GestServiceIdentityFeignConfig {

    @Bean
    public ServiceIdentityRequestInterceptor gestServiceIdentityInterceptor(
            ServiceTokenProvider serviceTokenProvider) {
        return new ServiceIdentityRequestInterceptor(serviceTokenProvider);
    }
}
