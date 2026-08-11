package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;
import java.util.Map;
import java.util.UUID;

/**
 * Thrown when a TipoGesto with the given id does not exist for the current tenant. Maps to HTTP 404
 * via GlobalExceptionHandler (error code: tipo-gesto-not-found).
 */
public class TipoGestoNotFoundException extends CodedException {

    public TipoGestoNotFoundException(UUID id) {
        super("tipo-gesto-not-found", "TipoGesto not found: " + id);
        withContext(Map.of("tipoGestoId", id.toString()));
    }
}
