package com.neuroassist.hist.domain.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuroassist.hist.domain.model.ForbiddenPayloadException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link LgpdPayloadGuard} — HIST-016, P5 acceptance criteria.
 *
 * <p>Pure domain test: zero Spring context, zero database. LgpdPayloadGuard is a POJO.
 */
class LgpdPayloadGuardTest {

    private final LgpdPayloadGuard guard = new LgpdPayloadGuard();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "image",
                "biometric",
                "photo",
                "fingerprint",
                "landmarks",
                "frame",
                "video",
                "embedding"
            })
    @DisplayName("Payload with forbidden key throws ForbiddenPayloadException")
    void validate_forbiddenKey_throwsException(String forbiddenKey) {
        Map<String, Object> payload = Map.of(forbiddenKey, "some_value");

        assertThatThrownBy(() -> guard.validate(payload))
                .isInstanceOf(ForbiddenPayloadException.class)
                .hasMessageContaining(forbiddenKey);
    }

    @Test
    @DisplayName("Null payload passes validation silently")
    void validate_nullPayload_passes() {
        assertThatCode(() -> guard.validate(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Empty payload passes validation")
    void validate_emptyPayload_passes() {
        assertThatCode(() -> guard.validate(Map.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Safe payload (no forbidden keys) passes validation")
    void validate_safePayload_passes() {
        Map<String, Object> payload = Map.of("action", "button_press", "confidence", 0.95);

        assertThatCode(() -> guard.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mixed payload — one forbidden key among safe keys throws exception")
    void validate_mixedPayload_throwsOnForbiddenKey() {
        Map<String, Object> payload = Map.of("action", "gesture", "image", "base64data");

        assertThatThrownBy(() -> guard.validate(payload))
                .isInstanceOf(ForbiddenPayloadException.class);
    }
}
