package com.neuroassist.acs.infrastructure.config;

import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure singleton that holds the currently active RSA signing key pair (ACS-026, Item A2).
 *
 * <p>Initially empty (null). Populated by {@link SigningKeyInitializer} during application startup
 * via {@link com.neuroassist.acs.application.service.SigningKeyService#getOrCreateActiveKey()}.
 *
 * <p>Used by {@link JwtEncoderConfig} (for dynamic JWK source) and by {@link
 * com.neuroassist.acs.infrastructure.persistence.adapter.SigningKeyPortAdapter} (for
 * getActiveSigningKey() / getPublicKeys()). Thread-safe via volatile.
 *
 * <p>SECURITY: the private key in the {@link RSAKey} is only held in JVM heap. It is never
 * serialized, logged, or exposed via any API endpoint.
 */
@Slf4j
@Component
public class SigningKeyHolder {

    private volatile RSAKey activeKey;

    /**
     * Returns the active RSA key pair.
     *
     * @return the current active {@link RSAKey}
     * @throws IllegalStateException if no key has been initialized yet
     */
    public RSAKey getActiveKey() {
        RSAKey key = activeKey;
        if (key == null) {
            throw new IllegalStateException(
                    "Signing key not yet initialized — SigningKeyInitializer may not have run");
        }
        return key;
    }

    /**
     * Returns the active RSA key pair, or {@code null} if not yet initialized.
     *
     * @return RSAKey or null
     */
    public RSAKey getActiveKeyOrNull() {
        return activeKey;
    }

    /**
     * Updates the active RSA key pair. Called by {@link SigningKeyInitializer} on startup and
     * during key rotation.
     *
     * @param rsaKey the new active key
     */
    public void updateKey(RSAKey rsaKey) {
        this.activeKey = rsaKey;
        log.info("SigningKeyHolder updated — kid={}", rsaKey != null ? rsaKey.getKeyID() : "null");
    }

    /** Returns true if the holder has been initialized with a key. */
    public boolean isInitialized() {
        return activeKey != null;
    }
}
