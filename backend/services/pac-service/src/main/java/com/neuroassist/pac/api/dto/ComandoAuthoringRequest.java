package com.neuroassist.pac.api.dto;

import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for Comando authoring — used for both create (POST) and edit (PUT) draft operations.
 *
 * <p>Bean Validation guards run before the service layer (SPEC-02 §5.1 guards 2-3):
 *
 * <ul>
 *   <li>rotulo and necessidade are validated non-blank at the HTTP boundary.
 *   <li>tempoMinGestoMs and tempoMaxGestoMs must be {@code >= 1} at binding time; the service
 *       additionally enforces tempoMinGestoMs {@code <= tempoMaxGestoMs}.
 * </ul>
 *
 * <p>primitivoSecundarioId is nullable — null means a single-primitive (non-composite) gesture.
 */
public record ComandoAuthoringRequest(
        @NotBlank String rotulo,
        @NotBlank String necessidade,
        @NotNull UUID primitivoPrincipalId,
        UUID primitivoSecundarioId,
        @NotNull Sensibilidade sensibilidade,
        @Min(1) int tempoMinGestoMs,
        @Min(1) int tempoMaxGestoMs,
        @NotNull Urgencia urgencia) {}
