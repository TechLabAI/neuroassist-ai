package com.neuroassist.pac.api.dto;

import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;

/**
 * HTTP request body for {@code POST .../comandos/{id}:recalibrate} (PAC-018).
 *
 * <p>All fields are optional. {@code null} means "keep the existing value". At least one field must
 * be non-null for the recalibrate to be meaningful (no guard enforced at the DTO level — a no-op
 * recalibrate is allowed and still bumps {@code calibracaoVersao}).
 *
 * <p>RULE-04: api.dto must not import infrastructure.* classes.
 */
public record RecalibrateRequest(
        Urgencia urgencia,
        Sensibilidade sensibilidade,
        Integer tempoMinGestoMs,
        Integer tempoMaxGestoMs) {}
