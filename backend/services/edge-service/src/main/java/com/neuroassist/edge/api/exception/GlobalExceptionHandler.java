package com.neuroassist.edge.api.exception;

import com.neuroassist.edge.shared.error.CodedException;
import com.neuroassist.edge.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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
     * Optimistic locking conflict — two concurrent writers raced on the same aggregate version.
     * Maps to HTTP 409 so clients can retry (EDGE P9 hardening). No @Transactional here — RULE-03.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        log.warn(
                "Optimistic lock conflict on {}: {}", ex.getPersistentClassName(), ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setType(URI.create("/errors/optimistic-lock"));
        detail.setTitle("Optimistic Lock Conflict");
        detail.setDetail("Resource was modified concurrently. Retry the operation.");
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            detail.setProperty("correlationId", correlationId);
        }
        return detail;
    }

    /**
     * Unhandled exceptions — map to the internal_error category (HTTP 500) via the factory so the
     * response payload still conforms to the documented schema.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return ProblemDetailFactory.forCode(null, ex, req);
    }
}
