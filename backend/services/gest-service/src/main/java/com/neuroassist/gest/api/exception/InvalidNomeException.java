package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;
import java.util.Map;

/**
 * Thrown when a TipoGesto nome fails basic validation (blank or exceeds max length). Maps to HTTP
 * 400 via GlobalExceptionHandler (error code: validation).
 */
public class InvalidNomeException extends CodedException {

    public InvalidNomeException(String reason) {
        super("validation", "Invalid nome: " + reason);
        withContext(Map.of("field", "nome", "reason", reason));
    }
}
