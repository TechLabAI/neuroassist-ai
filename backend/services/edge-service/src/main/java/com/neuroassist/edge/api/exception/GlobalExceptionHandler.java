package com.neuroassist.edge.api.exception;

import com.neuroassist.edge.shared.error.CodedException;
import com.neuroassist.edge.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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
     * Autorizacao negada por {@code @PreAuthorize} — HTTP 403, nao 500 (E2E-034).
     *
     * <p>Sem este handler a excecao caia no {@code @ExceptionHandler(Exception.class)} abaixo e
     * TODO 403 desta API era servido como {@code internal_error.unhandled} com status 500. O
     * chamador nao tinha como distinguir "voce nao tem essa authority" de "o EDGE quebrou" — e um
     * 5xx ainda derruba o circuit breaker do consumidor, transformando um erro de configuracao numa
     * queda aparente do servico. E a forma exata do defeito C-19, agora do lado do produtor.
     *
     * <p>Descoberto quando o GEST passou a chamar este servico e recebeu 500/"Access Denied" ao
     * resolver o pareamento de um dispositivo.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(
            AuthorizationDeniedException ex, HttpServletRequest req) {
        log.warn("Authorization denied on {}: {}", req.getRequestURI(), ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setType(URI.create("/errors/forbidden"));
        detail.setTitle("Forbidden");
        // Sem detalhar QUAL authority falta: isso enumeraria o modelo de permissoes para quem
        // ainda nao esta autorizado. O log acima carrega o necessario para diagnosticar.
        detail.setDetail("Access denied");
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

    /**
     * Malformed or structurally invalid request body (e.g. unrecognised property, wrong type,
     * broken JSON). Privacy invariant #6: unknown fields in the heartbeat body surface here as a
     * 400 — {@link com.neuroassist.edge.api.dto.HeartbeatRequest} rejects them in its canonical
     * constructor via a {@code @JsonAnySetter} catch-all, and Jackson wraps that rejection into
     * {@link HttpMessageNotReadableException}. The rule is scoped to that one payload; there is no
     * global {@code fail-on-unknown-properties} switch.
     *
     * <p>Overrides the parent's default (which returns a generic 400 without a problem-type URI) so
     * the response conforms to the documented error schema.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("https://neuroassist-ai.com/errors/validation"));
        pd.setTitle("Invalid request body");
        pd.setDetail(ex.getMostSpecificCause().getMessage());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            pd.setProperty("correlationId", correlationId);
        }
        return ResponseEntity.badRequest().body(pd);
    }
}
