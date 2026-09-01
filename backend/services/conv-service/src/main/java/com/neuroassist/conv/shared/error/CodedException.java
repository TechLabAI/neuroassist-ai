package com.neuroassist.conv.shared.error;

import java.util.Map;

/**
 * Domain exception carrying a stable error code (e.g. "cad.cliente.duplicado") declared in
 * error-model.arch.json. Throw this (or a subclass) from application/domain services; the
 * framework's GlobalExceptionHandler turns it into a ProblemDetail response with the right HTTP
 * status and extension fields.
 *
 * <p>For a soft-typed fluent throw site:
 *
 * <pre>
 *   throw new CodedException("cad.cliente.duplicado")
 *       .withContext(Map.of("clienteIdExistente", existingId));
 * </pre>
 */
public class CodedException extends RuntimeException {

    private final String code;
    private Map<String, Object> context = Map.of();

    public CodedException(String code) {
        super(code);
        this.code = code;
    }

    public CodedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public CodedException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> context() {
        return context;
    }

    public CodedException withContext(Map<String, Object> ctx) {
        this.context = ctx == null ? Map.of() : Map.copyOf(ctx);
        return this;
    }
}
