package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;
import com.neuroassist.gest.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 ProblemDetail exception handler for all GEST error types.
 *
 * <p>Handles the 13+ error codes declared in api-contract.md §7 via the code-based {@link
 * CodedException} approach backed by {@link ProblemDetailFactory}. Spring's built-in handler for
 * {@link MethodArgumentNotValidException} is also overridden to produce consistent {@code
 * application/problem+json} payloads.
 *
 * <p>Error codes handled (via CodedException):
 *
 * <ul>
 *   <li>{@code validation} (400) — malformed/blank field
 *   <li>{@code regression-reason-required} (400)
 *   <li>{@code tenant-mismatch} (403)
 *   <li>{@code insufficient-scope} (403)
 *   <li>{@code device-not-paired} (403)
 *   <li>{@code tipo-gesto-not-found} (404)
 *   <li>{@code no-active-profile} (404)
 *   <li>{@code profile-not-found} (404)
 *   <li>{@code tipo-gesto-duplicate} (409)
 *   <li>{@code illegal-transition} (409)
 *   <li>{@code profile-already-active} (409)
 *   <li>{@code eligibility-ineligible} (422)
 *   <li>{@code contract-violation} (422)
 *   <li>{@code engine-feature-ref-required} (422)
 *   <li>{@code empty-profile} (422)
 *   <li>{@code unknown-primitive} (422)
 *   <li>{@code unknown-profile-version} (422)
 *   <li>{@code primitive-not-in-profile} (422)
 *   <li>{@code tenant-not-found} (503)
 *   <li>{@code edge-unavailable} (503)
 *   <li>{@code internal} (500)
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Coded exceptions thrown by application/domain services. Resolved against the error catalogue
     * via ProblemDetailFactory (D-069). Handles all 13+ error types from api-contract.md §7.
     */
    @ExceptionHandler(CodedException.class)
    public ResponseEntity<ProblemDetail> handleCoded(CodedException ex, HttpServletRequest req) {
        log.warn("Coded exception code={} message={}", ex.code(), ex.getMessage());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /**
     * Jakarta Bean Validation constraint violations (from {@code @Valid} on controller params).
     * Maps to error code {@code validation} (400).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        log.debug("Constraint violation: {}", ex.getMessage());
        Map<String, Object> context =
                Map.of(
                        "violations",
                        ex.getConstraintViolations().stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.toList()));
        ProblemDetail pd = ProblemDetailFactory.forCode("validation", ex, req, context);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /**
     * Spring MVC method argument validation failure (from {@code @RequestBody @Valid}). Overrides
     * the parent handler to produce {@code application/problem+json} payloads.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.debug("Method argument not valid: {}", ex.getMessage());
        HttpServletRequest servletRequest =
                request instanceof ServletWebRequest swr ? swr.getRequest() : null;
        Map<String, Object> context =
                Map.of(
                        "fieldErrors",
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                                .collect(Collectors.toList()));
        ProblemDetail pd = ProblemDetailFactory.forCode("validation", ex, servletRequest, context);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /** Spring Security method-level access denied (e.g. wrong scope in @PreAuthorize) → 403. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(
            AuthorizationDeniedException ex, HttpServletRequest req) {
        log.debug("Authorization denied: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetailFactory.forCode("insufficient-scope", ex, req);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /** Spring Security authentication failure (missing or invalid token) → 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest req) {
        log.debug("Authentication failed: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetailFactory.forCode("unauthenticated", ex, req);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /**
     * Unhandled exceptions — map to the {@code internal} error category (HTTP 500) via the factory
     * so the response payload still conforms to the documented schema.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetailFactory.forCode("internal", ex, req);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
