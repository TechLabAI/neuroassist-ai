package com.neuroassist.pac.api.exception;

import com.neuroassist.pac.shared.error.CodedException;
import com.neuroassist.pac.shared.error.EligibilityGateUnavailableException;
import com.neuroassist.pac.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 ProblemDetail exception handler (PAC-022, P7).
 *
 * <p>All responses carry {@code Content-Type: application/problem+json} and a {@code correlationId}
 * extension field sourced from MDC (set by {@link
 * com.neuroassist.pac.infrastructure.security.CorrelationIdInterceptor}).
 *
 * <p>Architectural rules: RULE-03 — no {@code @Transactional} here; RULE-04 — no infrastructure
 * imports; RULE-05 — no api imports from infrastructure.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // -----------------------------------------------------------------------
    // Application / Domain exceptions
    // -----------------------------------------------------------------------

    /**
     * Coded exceptions thrown by application/domain services. Resolved against the
     * error-model.arch.json catalogue via ProblemDetailFactory (D-069).
     */
    @ExceptionHandler(CodedException.class)
    public ProblemDetail handleCoded(CodedException ex, HttpServletRequest req) {
        log.warn("Coded exception code={} message={}", ex.code(), ex.getMessage());
        return ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
    }

    /**
     * P5 — Emergency eligibility gate unavailable (SPEC-03 / ADR-005 fail-closed).
     *
     * <p>Returns HTTP 503 with {@code Retry-After} header so the caller knows how long to wait
     * before retrying. This exception is ONLY thrown for {@code urgencia=emergencia} when GEST is
     * unreachable or the circuit breaker is OPEN — it is a safety-critical response.
     */
    @ExceptionHandler(EligibilityGateUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleGateUnavailable(
            EligibilityGateUnavailableException ex, HttpServletRequest req) {
        log.warn(
                "Emergency eligibility gate unavailable retryAfter={}s message={}",
                ex.retryAfterSeconds(),
                ex.getMessage());
        ProblemDetail pd = ProblemDetailFactory.forCode("pac.emergency.gate-unavailable", ex, req);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).headers(headers).body(pd);
    }

    // -----------------------------------------------------------------------
    // Security exceptions (MVC layer — @PreAuthorize failures)
    // -----------------------------------------------------------------------

    /**
     * P7 — Scope enforcement: @PreAuthorize failures surface here as 403.
     *
     * <p>Maps to {@code /errors/insufficient-scope} so the frontend can display a consistent
     * forbidden alert (see FE error-model). NOTE: filter-chain AccessDeniedException (before MVC)
     * is handled by SecurityConfig.accessDeniedHandler().
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied path={} message={}", req.getRequestURI(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setType(URI.create("/errors/insufficient-scope"));
        pd.setTitle("Insufficient Scope");
        pd.setDetail("Insufficient scope to access this resource");
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty(
                "error",
                Map.of(
                        "code",
                        "pac.insufficient-scope",
                        "category",
                        "forbidden",
                        "correlationId",
                        Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp",
                        OffsetDateTime.now().toString(),
                        "context",
                        Map.of()));
        return pd;
    }

    // -----------------------------------------------------------------------
    // Validation exceptions
    // -----------------------------------------------------------------------

    /**
     * P7 — Bean Validation (@Valid) failures: override parent to inject correlationId and ensure
     * application/problem+json Content-Type.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<String> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(FieldError::getField)
                        .map(f -> f + ": " + fieldErrorMessage(ex, f))
                        .toList();
        log.warn("Validation failed fieldErrors={}", fieldErrors);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("/errors/validation"));
        pd.setTitle("Validation Failed");
        pd.setDetail("Validation failed for " + fieldErrors.size() + " field(s)");
        pd.setProperty(
                "error",
                Map.of(
                        "code",
                        "validation.failed",
                        "category",
                        "validation",
                        "correlationId",
                        Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp",
                        OffsetDateTime.now().toString(),
                        "context",
                        Map.of("fieldErrors", fieldErrors)));
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .headers(responseHeaders)
                .body(pd);
    }

    /** Constraint violation from @Validated on service/controller method parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        List<String> violations =
                ex.getConstraintViolations().stream().map(ConstraintViolation::getMessage).toList();
        log.warn("Constraint violation violations={}", violations);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create("/errors/validation"));
        pd.setTitle("Constraint Violation");
        pd.setDetail("Constraint violation: " + String.join("; ", violations));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty(
                "error",
                Map.of(
                        "code",
                        "validation.constraint-violation",
                        "category",
                        "validation",
                        "correlationId",
                        Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp",
                        OffsetDateTime.now().toString(),
                        "context",
                        Map.of("violations", violations)));
        return pd;
    }

    // -----------------------------------------------------------------------
    // Fallback
    // -----------------------------------------------------------------------

    /**
     * Unhandled exceptions — map to the internal_error category (HTTP 500) via the factory so the
     * response payload still conforms to the documented schema.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return ProblemDetailFactory.forCode(null, ex, req);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String fieldErrorMessage(MethodArgumentNotValidException ex, String fieldName) {
        return ex.getBindingResult().getFieldErrors().stream()
                .filter(fe -> fe.getField().equals(fieldName))
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("invalid");
    }
}
