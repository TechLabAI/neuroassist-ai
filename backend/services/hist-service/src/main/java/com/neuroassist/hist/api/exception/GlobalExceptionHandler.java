package com.neuroassist.hist.api.exception;

import com.neuroassist.hist.domain.model.ActorDirectoryUnavailableException;
import com.neuroassist.hist.domain.model.HistBadRequestException;
import com.neuroassist.hist.domain.model.HistDomainException;
import com.neuroassist.hist.domain.model.TenantMismatchException;
import com.neuroassist.hist.shared.error.CodedException;
import com.neuroassist.hist.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 ProblemDetail exception handler for hist-service. Maps domain exceptions, validation
 * errors, and CodedException to structured HTTP error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_BASE = "https://neuroassist-ai.com/errors/";

    /**
     * Bad request exceptions (incomplete filter, invalid time window, etc.) → 400 Bad Request.
     * These are structural request issues distinct from domain business-rule violations (422).
     */
    @ExceptionHandler(HistBadRequestException.class)
    public ProblemDetail handleHistBadRequest(HistBadRequestException ex, HttpServletRequest req) {
        log.warn("Bad request errorCode={} message={}", ex.errorCode(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Bad Request");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create(TYPE_BASE + ex.errorCode()));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty(
                "error",
                Map.of(
                        "code", ex.errorCode(),
                        "category", "validation",
                        "correlationId", Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp", OffsetDateTime.now().toString(),
                        "context", Map.of()));
        return pd;
    }

    /**
     * ACS actor directory unavailable (circuit breaker open or timeout) → 503 Service Unavailable.
     * Fail-closed: never widen data scope when ACS is down (HIST-012, ADR-005). The type URI uses
     * /errors/scope-unavailable to match the API contract.
     */
    @ExceptionHandler(ActorDirectoryUnavailableException.class)
    public ProblemDetail handleActorDirectoryUnavailable(
            ActorDirectoryUnavailableException ex, HttpServletRequest req) {
        log.warn("ACS actor directory unavailable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        pd.setTitle("Service Unavailable");
        pd.setDetail("The actor directory service is temporarily unavailable. Please retry later.");
        pd.setType(URI.create(TYPE_BASE + "scope-unavailable"));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty(
                "error",
                Map.of(
                        "code", "acs-directory-unavailable",
                        "category", "external_service",
                        "correlationId", Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp", OffsetDateTime.now().toString(),
                        "context", Map.of()));
        return pd;
    }

    /**
     * Tenant mismatch — request body empresaId does not match the JWT tenant claim → 403 Forbidden.
     * The type URI uses /errors/tenant-mismatch to match the API contract.
     */
    @ExceptionHandler(TenantMismatchException.class)
    public ProblemDetail handleTenantMismatch(TenantMismatchException ex, HttpServletRequest req) {
        log.warn("Tenant mismatch: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Forbidden");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create(TYPE_BASE + ex.errorCode()));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty(
                "error",
                Map.of(
                        "code", ex.errorCode(),
                        "category", "security",
                        "correlationId", Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp", OffsetDateTime.now().toString(),
                        "context", Map.of()));
        return pd;
    }

    /**
     * HIST domain exceptions (UnknownFactTypeException, ForbiddenPayloadException, …) → 422
     * Unprocessable Entity.
     */
    @ExceptionHandler(HistDomainException.class)
    public ProblemDetail handleHistDomainException(HistDomainException ex, HttpServletRequest req) {
        log.warn("Domain exception errorCode={} message={}", ex.errorCode(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Unprocessable request");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create(TYPE_BASE + ex.errorCode()));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty(
                "error",
                Map.of(
                        "code", ex.errorCode(),
                        "category", "business_rule",
                        "correlationId", Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp", OffsetDateTime.now().toString(),
                        "context", Map.of()));
        return pd;
    }

    /**
     * Bean Validation failures (@Valid on @RequestBody) → 400 Bad Request.
     *
     * <p>Override {@link ResponseEntityExceptionHandler#handleMethodArgumentNotValid} to avoid
     * ambiguous @ExceptionHandler mapping conflict with the parent class (Spring Boot 3.x).
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.warn("Validation failure: {}", ex.getMessage());
        String uri =
                request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "/";
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setDetail("One or more fields failed validation.");
        pd.setType(URI.create(TYPE_BASE + "validation"));
        pd.setInstance(URI.create(uri));
        pd.setProperty(
                "error",
                Map.of(
                        "code", "validation",
                        "category", "validation",
                        "correlationId", Objects.requireNonNullElse(MDC.get("correlationId"), ""),
                        "timestamp", OffsetDateTime.now().toString(),
                        "context", Map.of()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    /**
     * Coded exceptions thrown by application/domain services. Resolved against the
     * error-model.arch.json catalogue via ProblemDetailFactory (D-069).
     */
    @ExceptionHandler(CodedException.class)
    public ProblemDetail handleCoded(CodedException ex, HttpServletRequest req) {
        log.warn("Coded exception code={} message={}", ex.code(), ex.getMessage());
        return ProblemDetailFactory.forCode(ex.code(), ex, req, ex.context());
    }

    /** Unhandled exceptions — map to the internal_error category (HTTP 500). */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return ProblemDetailFactory.forCode(null, ex, req);
    }
}
