package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.service.EdgeInternalTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration for {@link MigProvisioningClient} (E2E-016).
 *
 * <p>Deliberately <b>not</b> {@code @Configuration}, for the same reason as {@link
 * EdgeInternalFeignConfig}: this package is component scanned, and a scanned configuration would
 * make {@link InternalServiceIdentityInterceptor} global — {@code PacPatientResolveClient} would
 * then call as acs-service instead of as the caregiver, silently widening a call whose
 * authorisation depends on <i>who asked</i>.
 *
 * <p>Reuses the tenant-less token provider: provisioning happens before the tenant has a schema, so
 * there is no tenant to scope the token to. That was already the right shape for ACS→EDGE
 * credential verification, and it is the right shape here for the same reason.
 */
public class MigInternalFeignConfig {

    @Bean
    public RequestInterceptor migInternalRequestInterceptor(
            EdgeInternalTokenProvider tokenProvider) {
        return new InternalServiceIdentityInterceptor(tokenProvider);
    }
}
