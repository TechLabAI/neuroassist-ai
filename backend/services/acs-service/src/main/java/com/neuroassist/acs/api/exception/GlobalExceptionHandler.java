package com.neuroassist.acs.api.exception;

import com.neuroassist.acs.shared.error.CaregiverNotFoundException;
import com.neuroassist.acs.shared.error.CodedException;
import com.neuroassist.acs.shared.error.CrossTenantLinkException;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import com.neuroassist.acs.shared.error.LastCaregiverLinkException;
import com.neuroassist.acs.shared.error.LoginDuplicateException;
import com.neuroassist.acs.shared.error.ProblemDetailFactory;
import com.neuroassist.acs.shared.error.TenantMismatchException;
import com.neuroassist.acs.shared.error.TenantNotActiveException;
import com.neuroassist.acs.shared.error.TenantNotFoundException;
import com.neuroassist.acs.shared.error.TenantProvisioningTimeoutException;
import com.neuroassist.acs.shared.error.TenantSuspendedException;
import com.neuroassist.acs.shared.error.UnknownPatientException;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler (RFC 7807 ProblemDetail) for acs-service (ACS-020).
 *
 * <p>All domain + security + validation exceptions are mapped to ProblemDetail responses. SECURITY:
 * no credential, hash, or private key data leaks in error responses. correlationId is injected from
 * MDC into every ProblemDetail via ProblemDetailFactory.
 *
 * <p>RULE-04: no infrastructure dependency here.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // -----------------------------------------------------------------------
    // Domain exceptions — 4xx
    // -----------------------------------------------------------------------

    /**
     * 401 — Invalid credentials (non-discriminating: wrong login OR wrong senha). SECURITY: message
     * never discriminates between login vs. senha mismatch.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest req) {
        log.debug("Authentication failed (invalid credentials): {}", ex.code());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    /** 404 — Tenant not found. */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTenantNotFound(
            TenantNotFoundException ex, HttpServletRequest req) {
        log.warn("Tenant not found: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    /** 409 — Tenant suspended. */
    @ExceptionHandler(TenantSuspendedException.class)
    public ResponseEntity<ProblemDetail> handleTenantSuspended(
            TenantSuspendedException ex, HttpServletRequest req) {
        log.warn("Access to suspended tenant: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /** 409 — Tenant not active (e.g. still PROVISIONING). */
    @ExceptionHandler(TenantNotActiveException.class)
    public ResponseEntity<ProblemDetail> handleTenantNotActive(
            TenantNotActiveException ex, HttpServletRequest req) {
        log.warn("Tenant not active: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /** 504 — Tenant provisioning timed out. */
    @ExceptionHandler(TenantProvisioningTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleProvisioningTimeout(
            TenantProvisioningTimeoutException ex, HttpServletRequest req) {
        log.error("Provisioning timeout for tenant: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.GATEWAY_TIMEOUT.value());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(pd);
    }

    /** 502 — Upstream service unavailable (MIG, PAC, HIST). */
    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUpstreamUnavailable(
            UpstreamUnavailableException ex, HttpServletRequest req) {
        log.error("Upstream unavailable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.BAD_GATEWAY.value());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(pd);
    }

    /** 404 — Caregiver not found. */
    @ExceptionHandler(CaregiverNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleCaregiverNotFound(
            CaregiverNotFoundException ex, HttpServletRequest req) {
        log.warn("Caregiver not found: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    /**
     * 409 — Login already exists in the tenant. SECURITY: message is generic — does not
     * discriminate exact collision cause.
     */
    @ExceptionHandler(LoginDuplicateException.class)
    public ResponseEntity<ProblemDetail> handleLoginDuplicate(
            LoginDuplicateException ex, HttpServletRequest req) {
        log.warn("Login duplicate: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /**
     * 409 — Attempt to revoke the last active caregiver link (SAFETY-CRITICAL D-204). Patient must
     * always retain at least one active caregiver for alarm routing.
     */
    @ExceptionHandler(LastCaregiverLinkException.class)
    public ResponseEntity<ProblemDetail> handleLastCaregiverLink(
            LastCaregiverLinkException ex, HttpServletRequest req) {
        log.error("SAFETY-CRITICAL (D-204): last caregiver link revoke blocked: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /** 422 — Cross-tenant link attempt (different tenant IDs for caregiver and patient). */
    @ExceptionHandler(CrossTenantLinkException.class)
    public ResponseEntity<ProblemDetail> handleCrossTenantLink(
            CrossTenantLinkException ex, HttpServletRequest req) {
        log.warn("Cross-tenant link attempt: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        return ResponseEntity.unprocessableEntity().body(pd);
    }

    /** 422 — Unknown patient (PAC returned 404 for patient). */
    @ExceptionHandler(UnknownPatientException.class)
    public ResponseEntity<ProblemDetail> handleUnknownPatient(
            UnknownPatientException ex, HttpServletRequest req) {
        log.warn("Unknown patient: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        return ResponseEntity.unprocessableEntity().body(pd);
    }

    /** 403 — Caregiver's tenant does not match the requested tenant context. */
    @ExceptionHandler(TenantMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTenantMismatch(
            TenantMismatchException ex, HttpServletRequest req) {
        log.warn("Tenant mismatch: {}", ex.context());
        ProblemDetail pd = ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
        pd.setStatus(HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
    }

    /** Generic coded exceptions from domain/application services. */
    @ExceptionHandler(CodedException.class)
    public ProblemDetail handleCoded(CodedException ex, HttpServletRequest req) {
        log.warn("Coded exception code={} message={}", ex.code(), ex.getMessage());
        return ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
    }

    // -----------------------------------------------------------------------
    // Spring Security exceptions — 401 / 403
    // -----------------------------------------------------------------------

    /** 401 — Spring Security AuthenticationException (filter-level auth failure). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest req) {
        log.debug("Spring Security authentication failure: {}", ex.getMessage());
        ProblemDetail pd =
                ProblemDetailFactory.forCode("acs.auth.unauthenticated", ex, req, Map.of());
        pd.setStatus(HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    /** 403 — Spring Security AccessDeniedException (insufficient authority). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail pd =
                ProblemDetailFactory.forCode("acs.auth.insufficient-authority", ex, req, Map.of());
        pd.setStatus(HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
    }

    // -----------------------------------------------------------------------
    // Validation exceptions — 400
    // -----------------------------------------------------------------------

    /**
     * 400 — Bean Validation failure (@Valid / @Validated on controller params). Overrides parent to
     * inject correlationId and structured field errors.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, Object>> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                (FieldError fe) ->
                                        Map.<String, Object>of(
                                                "field",
                                                fe.getField(),
                                                "message",
                                                fe.getDefaultMessage() != null
                                                        ? fe.getDefaultMessage()
                                                        : "invalid"))
                        .toList();

        ProblemDetail pd =
                ProblemDetailFactory.forCode(
                        "acs.validation.invalid-request", ex, null, Map.of("errors", fieldErrors));
        pd.setStatus(HttpStatus.BAD_REQUEST.value());
        pd.setTitle("Validation Failed");
        pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(pd);
    }

    /** 400 — Invalid enum value or argument (e.g. unknown CuidadorPapel or TenantTipo). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Invalid argument: {}", ex.getMessage());
        ProblemDetail pd =
                ProblemDetailFactory.forCode("acs.validation.invalid-value", ex, req, Map.of());
        pd.setStatus(HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(pd);
    }

    // -----------------------------------------------------------------------
    // Fallback — 500
    // -----------------------------------------------------------------------

    /**
     * 500 — Unhandled exceptions. Logs full stack trace; returns generic message. SECURITY: stack
     * trace never returned to client.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return ProblemDetailFactory.forCode(null, ex, req);
    }
}
