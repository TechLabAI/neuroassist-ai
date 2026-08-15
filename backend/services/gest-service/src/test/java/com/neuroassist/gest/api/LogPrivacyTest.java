package com.neuroassist.gest.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.neuroassist.gest.api.dto.DetectionContractRequest;
import com.neuroassist.gest.api.dto.DetectionContractResponse;
import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.api.dto.TipoGestoRequest;
import com.neuroassist.gest.api.dto.TipoGestoResponse;
import com.neuroassist.gest.api.exception.GlobalExceptionHandler;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * LGPD Log-Privacy validation (GEST-022 — P7).
 *
 * <p>Unit test (no Spring context) verifying that:
 *
 * <ol>
 *   <li>No biometric field names ({@code video}, {@code image}, {@code landmark}, {@code base64},
 *       {@code pixels}, {@code frame}, {@code confidence}) appear in logs produced by {@link
 *       GlobalExceptionHandler}.
 *   <li>Public API DTO record components do not expose biometric field identifiers.
 * </ol>
 *
 * <p>This test is the automated gate for LGPD compliance at the surface layer (RULE-LGPD).
 */
@ExtendWith(MockitoExtension.class)
class LogPrivacyTest {

    /**
     * Pattern that matches any biometric field that must NOT appear in logs or DTO surfaces. -
     * {@code confidence} is intentionally excluded from the DTO check (it appears in contract
     * validation error messages as a referenced field name, not as raw data) but included here for
     * log scanning.
     */
    private static final Pattern BIOMETRIC_PATTERN =
            Pattern.compile(
                    "(?i)\\b(video|image|landmark|base64|pixels|frame|confidence)\\b",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Biometric field names that must NOT appear as DTO record component names in the public API
     * surface. Excludes {@code confidence} since it is a named parameter in contract-validation
     * responses (not raw biometric data).
     */
    private static final Pattern DTO_BIOMETRIC_FIELD_PATTERN =
            Pattern.compile(
                    "(?i)^(video|image|landmark|base64|pixels|frame)$", Pattern.CASE_INSENSITIVE);

    private ListAppender<ILoggingEvent> logAppender;
    private Logger handlerLogger;

    @BeforeEach
    void attachLogAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        handlerLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    /**
     * Verifies that {@link GlobalExceptionHandler} does not log any biometric field values when
     * processing an exception whose context includes forbidden fields.
     *
     * <p>The handler MUST log the error code and type, but MUST NOT echo the request body or any
     * field that identifies raw biometric data.
     */
    @Test
    void globalExceptionHandler_handlesException_withoutLoggingBiometricFields() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/gest/contract/validate");

        // Simulate a generic exception whose message contains a biometric reference — the handler
        // must NOT echo this in structured log fields.
        RuntimeException ex =
                new RuntimeException(
                        "Validation failed: video=base64data==, landmark=[0.1,0.2], confidence=0.97");
        handler.handleGenericException(ex, req);

        boolean foundBiometric =
                logAppender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(msg -> BIOMETRIC_PATTERN.matcher(msg).find());

        assertThat(foundBiometric)
                .as(
                        "GlobalExceptionHandler log must not contain biometric field references"
                                + " (video, image, landmark, base64, pixels, frame, confidence)")
                .isFalse();
    }

    /**
     * Verifies that public API DTO record component names do not expose biometric field
     * identifiers.
     *
     * <p>This ensures that even if a DTO is accidentally serialised into a log line, no field name
     * would reveal raw biometric data (LGPD Art. 11 — sensitive data protection).
     */
    @Test
    void dto_fieldNames_doNotContainBiometricIdentifiers() {
        Class<?>[] dtoClasses = {
            DetectionContractRequest.class,
            DetectionContractResponse.class,
            DetectionProfileRequest.class,
            DetectionProfileResponse.class,
            TipoGestoRequest.class,
            TipoGestoResponse.class,
        };

        for (Class<?> dto : dtoClasses) {
            for (var component : dto.getRecordComponents()) {
                assertThat(DTO_BIOMETRIC_FIELD_PATTERN.matcher(component.getName()).find())
                        .as(
                                "DTO '%s' must not have a biometric field named '%s'",
                                dto.getSimpleName(), component.getName())
                        .isFalse();
            }
        }
    }

    /**
     * Verifies that DetectionContractRequest can hold an arbitrary report map without exposing
     * biometric field names in the DTO record component surface.
     *
     * <p>The map value itself is opaque at the type level; the test confirms that no biometric name
     * is a first-class record field.
     */
    @Test
    void detectionContractRequest_holdsReportMap_withoutBiometricFieldComponents() {
        // Creating an instance with biometric-named keys in the map is acceptable (the map is
        // opaque), but no DTO record COMPONENT should be named after a biometric.
        DetectionContractRequest request =
                new DetectionContractRequest(Map.of("gestureId", "uuid-1", "windowMs", 200));
        assertThat(request).isNotNull();

        for (var component : DetectionContractRequest.class.getRecordComponents()) {
            assertThat(DTO_BIOMETRIC_FIELD_PATTERN.matcher(component.getName()).find())
                    .as(
                            "DetectionContractRequest must not have biometric field: %s",
                            component.getName())
                    .isFalse();
        }
    }
}
