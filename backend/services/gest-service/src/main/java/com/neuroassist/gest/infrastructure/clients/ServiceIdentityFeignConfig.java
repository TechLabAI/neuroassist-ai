package com.neuroassist.gest.infrastructure.clients;

import com.neuroassist.gest.infrastructure.security.ServiceTokenProvider;
import org.springframework.context.annotation.Bean;

/**
 * Configuração por-cliente dos Feign que falam com identidade de serviço (GEST → EDGE, GEST → PAC).
 *
 * <p>Deliberadamente <b>sem</b> {@code @Configuration}: o Spring Cloud a registra no contexto filho
 * do cliente via {@code @FeignClient(configuration = ...)}. Se o component scan a pegasse, o
 * interceptor de identidade viraria global e sequestraria o {@code Authorization} de todo cliente
 * Feign do serviço.
 */
public class ServiceIdentityFeignConfig {

    @Bean
    public ServiceIdentityRequestInterceptor serviceIdentityInterceptor(
            ServiceTokenProvider serviceTokenProvider) {
        return new ServiceIdentityRequestInterceptor(serviceTokenProvider);
    }
}
