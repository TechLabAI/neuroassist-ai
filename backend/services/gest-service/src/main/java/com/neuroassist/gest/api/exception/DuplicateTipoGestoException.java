package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;
import java.util.Map;

/**
 * Thrown when registering a TipoGesto whose name already exists (case-insensitive) for the current
 * tenant in a non-deprecated state. Maps to HTTP 409 via GlobalExceptionHandler (error code:
 * tipo-gesto-duplicate).
 */
public class DuplicateTipoGestoException extends CodedException {

    public DuplicateTipoGestoException(String nome) {
        super(
                "tipo-gesto-duplicate",
                "A TipoGesto with name '" + nome + "' already exists for this tenant");
        withContext(Map.of("nome", nome));
    }
}
