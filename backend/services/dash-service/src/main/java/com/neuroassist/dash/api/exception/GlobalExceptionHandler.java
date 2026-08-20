package com.neuroassist.dash.api.exception;

import com.neuroassist.dash.shared.error.CodedException;
import com.neuroassist.dash.shared.error.PatientNotInScopeException;
import com.neuroassist.dash.shared.error.ProblemDetailFactory;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * PatientNotInScopeException — cuidador nao tem acesso ao paciente. HTTP 403.
     */
    @ExceptionHandler(PatientNotInScopeException.class)
    public ResponseEntity<ProblemDetail> handlePatientNotInScope(
            PatientNotInScopeException ex, HttpServletRequest req) {
        log.warn("Scope violation code={} message={}", ex.code(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setDetail(ex.getMessage());
        pd.setProperty(
                "error",
                java.util.Map.of(
                        "code", ex.code(),
                        "category", "forbidden",
                        "correlationId",
                        java.util.Objects.requireNonNullElse(
                                org.slf4j.MDC.get("correlationId"), ""),
                        "timestamp", java.time.OffsetDateTime.now().toString(),
                        "context", ex.context()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
    }

    /**
     * FeignException — upstream hard-dependency indisponivel. HTTP 503.
     * PAC, ACS e HIST sao hard: erro e propagado sem silenciar.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ProblemDetail> handleUpstreamUnavailable(
            FeignException ex, HttpServletRequest req) {
        log.error("Upstream Feign error status={} message={}", ex.status(), ex.getMessage());
        int upstreamStatus = ex.status();
        // Relay ProblemDetail fielmente se o upstream retornou 4xx (ex.: 422 do PAC)
        if (upstreamStatus >= 400 && upstreamStatus < 500) {
            ProblemDetail pd = ProblemDetail.forStatus(upstreamStatus);
            pd.setDetail("upstream-error: " + ex.getMessage());
            pd.setProperty("correlationId",
                    java.util.Objects.requireNonNullElse(org.slf4j.MDC.get("correlationId"), ""));
            return ResponseEntity.status(upstreamStatus).body(pd);
        }
        // 5xx ou erro de conexao → 503
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        pd.setDetail("upstream-unavailable: " + ex.getMessage());
        pd.setProperty("correlationId",
                java.util.Objects.requireNonNullElse(org.slf4j.MDC.get("correlationId"), ""));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
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
