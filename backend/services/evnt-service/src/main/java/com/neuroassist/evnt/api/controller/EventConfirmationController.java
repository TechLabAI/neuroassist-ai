package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import com.neuroassist.evnt.shared.error.CodedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * Confirmation of an Evento by a caregiver.
 *
 * <p>Two entry points on purpose, because two callers exist and they are not the same kind of
 * caller:
 *
 * <ul>
 *   <li>{@code POST /{id}/confirmation} — a caregiver's own JWT, carrying {@code
 *       evnt.evento.confirmar}. The body names the confirmer.
 *   <li>{@code POST /{id}/confirm?caregiverId=} — dash-service proxying its panel, with its own
 *       service-identity token ({@code SVC_DASH}). This is the shape {@code EvntClient} has been
 *       calling all along while evnt-service exposed nothing at that path.
 * </ul>
 *
 * <p>Both funnel into the same use case, so the terminal-lock and tenant-isolation invariants of
 * EVNT-022/EVNT-023 cannot be bypassed by picking the other door.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventConfirmationController {

    private final EventConfirmationUseCase eventConfirmationUseCase;

    /**
     * Quem confirmou vem do <b>token</b>, nunca do corpo.
     *
     * <p>Ate aqui {@code confirmedBy} era lido do corpo da requisicao e usado como esta. Um
     * chamador autenticado podia declarar ser outra pessoa, e o fato {@code evento.confirmado} —
     * append-only e encadeado por hash — gravava o ator declarado. Verificado ao vivo: autenticado
     * como {@code PACIENTE_APOIO}, declarando o id da coordenadora, a resposta foi 200 e o {@code
     * actor.caregiverId} do fato ficou sendo o da coordenadora.
     *
     * <p>Esse registro e o que responde "quem atendeu?" depois de um incidente. Um ator
     * autodeclarado torna a pergunta irrespondivel, e o encadeamento por hash — que existe para
     * provar que ninguem adulterou a trilha — passa a garantir a integridade de uma mentira.
     *
     * <p>Um {@code confirmedBy} divergente do {@code sub} e <b>recusado</b>, nao silenciosamente
     * sobrescrito: sobrescrever esconderia um cliente com defeito enquanto ele continua enviando o
     * campo errado.
     */
    @PostMapping("/{id}/confirmation")
    @PreAuthorize("hasAuthority('evnt.event-confirmation.confirmar')")
    public ResponseEntity<Void> confirm(
            @PathVariable UUID id,
            @RequestBody ConfirmationRequest body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String requestTenant,
            Authentication auth) {
        String confirmador = subjectOf(auth);
        if (confirmador == null) {
            throw new CodedException(
                    "confirmador-unresolved", "Authenticated token carries no subject");
        }
        if (body.confirmedBy() != null
                && !body.confirmedBy().isBlank()
                && !body.confirmedBy().equals(confirmador)) {
            log.warn(
                    "Recusado: token de {} declarou confirmedBy={} — identidade nao pode vir do"
                            + " corpo",
                    confirmador,
                    body.confirmedBy());
            throw new CodedException(
                    "confirmador-divergente",
                    "confirmedBy does not match the authenticated subject");
        }
        eventConfirmationUseCase.confirm(id, body.verdict(), confirmador, requestTenant);
        return ResponseEntity.ok().build();
    }

    /** O {@code sub} do JWT — o unico lugar de onde a identidade de quem confirma pode vir. */
    private static String subjectOf(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }

    /**
     * dash-service's write-through. It sends no verdict, because the panel's "confirm" button means
     * the alarm was real — a false positive is reported through its own {@code
     * /panel/events/{id}/confirm?feedback=} surface, which maps onto the endpoint above.
     */
    /**
     * <b>Aqui o {@code caregiverId} continua vindo de fora, e e correto.</b> Quem chama e o
     * dash-service com sua identidade de servico, agindo <i>em nome</i> da cuidadora que apertou o
     * botao — o {@code sub} deste token e {@code dash-service}, nao uma pessoa. E a mesma forma da
     * excecao do token de servico sem tenant: um servico agindo por alguem e legitimo, desde que
     * seja um servico e nao um usuario.
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('SVC_DASH')")
    public ResponseEntity<Void> confirmFromPanel(
            @PathVariable UUID id,
            @RequestParam("caregiverId") String caregiverId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String requestTenant) {
        eventConfirmationUseCase.confirm(id, "real", caregiverId, requestTenant);
        return ResponseEntity.ok().build();
    }

    public record ConfirmationRequest(String verdict, String confirmedBy) {}
}
