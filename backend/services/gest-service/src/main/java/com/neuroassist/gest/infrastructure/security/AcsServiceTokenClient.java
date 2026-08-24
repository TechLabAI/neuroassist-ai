package com.neuroassist.gest.infrastructure.security;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link ServiceTokenClient} over HTTP against the ACS service-identity endpoint.
 *
 * <p>Deliberately <b>not</b> a {@code @FeignClient}: the global Feign interceptors ({@code
 * feignAuthPropagationInterceptor}) would attach the caller's JWT to the token request, and this
 * call must be authenticated only by the client secret.
 *
 * <p>Never logs the secret nor the returned token.
 */
@Slf4j
@Component
public class AcsServiceTokenClient implements ServiceTokenClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public AcsServiceTokenClient(
            RestClient.Builder builder,
            @Value("${neuroassist.acs.service-token.url}") String acsBaseUrl,
            @Value("${neuroassist.acs.service-token.client-id}") String clientId,
            @Value("${neuroassist.acs.service-token.client-secret}") String clientSecret,
            @Value("${neuroassist.acs.service-token.timeout-ms:3000}") long timeoutMs) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = builder.baseUrl(acsBaseUrl).requestFactory(requestFactory).build();
        log.info(
                "ACS service-identity client configured (GEST): url={} clientId={}", acsBaseUrl, clientId);
    }

    @Override
    public ServiceTokenResponse requestToken(String tenantId) {
        return restClient
                .post()
                .uri("/oauth2/token/service")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ServiceTokenRequestBody(clientId, clientSecret, tenantId))
                .retrieve()
                .body(ServiceTokenResponse.class);
    }

    /** Request payload of {@code POST /oauth2/token/service}. Never logged. */
    record ServiceTokenRequestBody(String clientId, String clientSecret, String tenantId) {
        @Override
        public String toString() {
            return "ServiceTokenRequestBody[clientId=" + clientId + ", clientSecret=***]";
        }
    }
}
