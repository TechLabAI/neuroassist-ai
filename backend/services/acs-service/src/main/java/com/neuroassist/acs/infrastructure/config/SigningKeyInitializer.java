package com.neuroassist.acs.infrastructure.config;

import com.neuroassist.acs.application.service.SigningKeyService;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializes the active RSA signing key on application startup (ACS-026, Item A2).
 *
 * <p>Runs after the full Spring context (including JPA/DataSource) is ready. Calls {@link
 * SigningKeyService#getOrCreateActiveKey()} to load or create the persisted key, then stores it in
 * {@link SigningKeyHolder} for use by {@link JwtEncoderConfig} and {@link
 * com.neuroassist.acs.infrastructure.persistence.adapter.SigningKeyPortAdapter}.
 *
 * <p>{@code @Order(1)} ensures this runs before other ApplicationRunners that might need JWT
 * encoding (e.g., health-check warmup tasks).
 *
 * <p>SECURITY: the private key from the database is decrypted in memory only for the duration of
 * this run call, then passed to {@link SigningKeyHolder}. It is never logged.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SigningKeyInitializer implements ApplicationRunner {

    private final SigningKeyService signingKeyService;
    private final SigningKeyHolder signingKeyHolder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("SigningKeyInitializer: loading active RSA signing key from database...");
        try {
            RSAKey activeKey = signingKeyService.getOrCreateActiveKey();
            signingKeyHolder.updateKey(activeKey);
            log.info(
                    "SigningKeyInitializer: active signing key initialized — kid={}",
                    activeKey.getKeyID());
        } catch (Exception e) {
            log.error(
                    "SigningKeyInitializer: FAILED to initialize signing key — service will be"
                            + " unable to issue or validate JWT tokens: {}",
                    e.getMessage(),
                    e);
            // Do not rethrow — allows service to start even if DB is temporarily unavailable;
            // JWT operations will fail until key is initialized via retry or restart.
        }
    }
}
