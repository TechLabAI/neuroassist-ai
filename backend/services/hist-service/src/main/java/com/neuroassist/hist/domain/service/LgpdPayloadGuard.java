package com.neuroassist.hist.domain.service;

import com.neuroassist.hist.domain.model.ForbiddenPayloadException;
import java.util.Map;
import java.util.Set;

/**
 * Domain guard — rejects payloads that contain biometric or image-derived keys forbidden by LGPD
 * (HIST-016, P5). This is a pure POJO: zero Spring, zero JPA, fully testable without container.
 *
 * <p>Forbidden keys (case-sensitive): {@code landmarks}, {@code image}, {@code frame}, {@code
 * video}, {@code embedding}, {@code biometric}, {@code photo}, {@code fingerprint}.
 *
 * <p>Must be registered as a {@code @Bean} in {@code DomainServiceConfig} (CLAUDE.md — domain
 * services are POJOs and must be beans).
 */
public class LgpdPayloadGuard {

    /** Keys that must never appear in any stored audit-fact payload (LGPD Art. 11). */
    static final Set<String> FORBIDDEN_KEYS =
            Set.of(
                    "landmarks",
                    "image",
                    "frame",
                    "video",
                    "embedding",
                    "biometric",
                    "photo",
                    "fingerprint");

    /**
     * Validates that {@code payload} does not contain any forbidden biometric key.
     *
     * @param payload the audit-fact payload (may be {@code null} — silently accepted)
     * @throws ForbiddenPayloadException if any key in {@code payload} is in the forbidden list
     */
    public void validate(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        for (String key : payload.keySet()) {
            if (FORBIDDEN_KEYS.contains(key)) {
                throw new ForbiddenPayloadException(key);
            }
        }
    }
}
