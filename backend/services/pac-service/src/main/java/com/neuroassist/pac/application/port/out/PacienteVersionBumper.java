package com.neuroassist.pac.application.port.out;

import java.util.UUID;

/**
 * Outbound port — PacienteVersionBumper (PAC-019).
 *
 * <p>Atomically increments the patient's {@code catalogoVersao} counter within the caller's
 * {@code @Transactional} boundary. Called after every change to a published Comando: publish,
 * recalibrate, retire.
 *
 * <p>{@code catalogoVersao} drives the HTTP ETag for {@code GET .../catalogo-ativo} and enables
 * conditional GET with {@code 304 Not Modified}.
 *
 * <p>Implementations must NOT declare {@code @Transactional} (RULE-03) — the transaction boundary
 * is in {@code application.service}.
 */
public interface PacienteVersionBumper {

    /**
     * Atomically increments {@code catalogoVersao} by 1 for the given patient and returns the new
     * version value.
     *
     * <p>Must be called within an existing {@code @Transactional} context so the bump is committed
     * atomically with the lifecycle change.
     *
     * @param pacienteId the patient whose catalog version to bump
     * @return the new {@code catalogoVersao} after increment
     * @throws com.neuroassist.pac.shared.error.CodedException (not_found.paciente) if the patient
     *     is not found in the current tenant schema
     */
    int bumpAndGet(UUID pacienteId);

    /**
     * Returns the current {@code catalogoVersao} without modifying it. Used to build the ETag in
     * {@code GET .../catalogo-ativo} response.
     *
     * @param pacienteId the patient whose catalog version to read
     * @return the current {@code catalogoVersao}, or {@code 0} if not found
     */
    int getCatalogoVersao(UUID pacienteId);
}
