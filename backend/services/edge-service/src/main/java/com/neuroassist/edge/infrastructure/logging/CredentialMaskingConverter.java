package com.neuroassist.edge.infrastructure.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * CredentialMaskingConverter — EDGE-018 (P7).
 *
 * <p>Logback PatternLayout converter registered as {@code %maskedMsg} in {@code
 * logback-spring.xml}. Masks plaintext credential token patterns from log messages as a
 * defence-in-depth measure (LGPD privacy invariant: zero credential in logs).
 *
 * <p>Patterns masked:
 *
 * <ul>
 *   <li>{@code cs_[hex]} — client secrets (issued in DeviceRegistrationService.enroll, EDGE-006)
 *   <li>{@code et_[hex]} — enrollment tokens (issued in DeviceRegistrationService.register,
 *       EDGE-005)
 * </ul>
 *
 * <p><strong>Note:</strong> production code already enforces that neither {@code plainSecret} nor
 * {@code plainToken} are ever passed to log statements. This converter is a defence-in-depth guard
 * against accidental future logging of these values.
 *
 * <p>Privacy test (EDGE-018 criteria): grep over log output for {@code cs_} and {@code et_} after
 * an enroll operation must return zero matches against plaintext values (masked as {@code
 * cs_***REDACTED***}).
 *
 * <p>Registration in {@code logback-spring.xml}:
 *
 * <pre>{@code
 * <conversionRule conversionWord="maskedMsg"
 *     converterClass="com.neuroassist.edge.infrastructure.logging.CredentialMaskingConverter"/>
 * }</pre>
 */
public class CredentialMaskingConverter extends ClassicConverter {

    /**
     * Matches credential token prefixes followed by at least 8 hex characters. Covers: cs_[64 hex
     * chars] (client secret) and et_[64 hex chars] (enrollment token).
     */
    private static final Pattern CREDENTIAL_PATTERN =
            Pattern.compile("(cs_|et_)[a-fA-F0-9]{8,}", Pattern.CASE_INSENSITIVE);

    /** Replacement: keep prefix so log analysis can identify the credential type. */
    private static final String REPLACEMENT = "$1***REDACTED***";

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null || message.isEmpty()) {
            return "";
        }
        return CREDENTIAL_PATTERN.matcher(message).replaceAll(REPLACEMENT);
    }
}
