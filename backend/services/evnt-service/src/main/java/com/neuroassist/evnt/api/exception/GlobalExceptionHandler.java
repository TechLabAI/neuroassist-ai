package com.neuroassist.evnt.api.exception;

import com.neuroassist.evnt.shared.error.CodedException;
import com.neuroassist.evnt.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
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
     * Unhandled exceptions — map to the internal_error category (HTTP 500) via the factory so the
     * response payload still conforms to the documented schema.
     */
    /**
     * Autorizacao negada por {@code @PreAuthorize} — 403, nao 500.
     *
     * <p>Sem este handler a excecao caia no {@code @ExceptionHandler(Exception.class)} abaixo e
     * TODO 403 deste servico era servido como {@code internal_error} com status 500. Duas
     * consequencias: o chamador nao distingue "voce nao tem essa authority" de "o EVNT quebrou", e
     * um 5xx <b>derruba o circuit breaker</b> de quem chama, enquanto um 4xx seria ignorado — um
     * erro de configuracao virando queda aparente do servico. E a forma do defeito C-19, aqui do
     * lado do produtor; o mesmo ja foi corrigido no edge-service (E2E-034).
     *
     * <p>Encontrado pela suite E2E e confirmado ao vivo: o papel PACIENTE_APOIO, sem {@code
     * evnt.event-query.read}, recebia 500 ao listar alarmes.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(
            AuthorizationDeniedException ex, HttpServletRequest req) {
        log.warn("Authorization denied on {}: {}", req.getRequestURI(), ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setType(URI.create("/errors/forbidden"));
        detail.setTitle("Forbidden");
        // Sem dizer QUAL authority falta: isso enumeraria o modelo de permissoes para quem ainda
        // nao esta autorizado. O log acima carrega o necessario para diagnosticar.
        detail.setDetail("Access denied");
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return ProblemDetailFactory.forCode(null, ex, req);
    }
}
