package com.neuroassist.hist.domain.model;

import java.util.Optional;

/**
 * Controlled vocabulary of audit fact types (v1 seed). dot-notation codes are the canonical form
 * used in the API contract and stored in the ledger. Enum constants follow Java UPPER_SNAKE_CASE;
 * use {@link #code()} for the external representation.
 */
public enum FactType {
    EVENTO_CONFIRMADO("evento.confirmado"),
    EVENTO_DESCARTADO("evento.descartado"),
    EVENTO_FEEDBACK_REGISTRADO("evento.feedback.registrado"),
    COMANDO_SENSIBILIDADE_EDITADA("comando.sensibilidade.editada"),
    COMANDO_RECALIBRADO("comando.recalibrado"),
    ACESSO_CONCEDIDO("acesso.concedido"),
    ACESSO_REVOGADO("acesso.revogado"),
    DISPOSITIVO_PAREADO("dispositivo.pareado"),
    DISPOSITIVO_DESPAREADO("dispositivo.despareado"),
    REGISTRO_CORRIGIDO("registro.corrigido");

    private final String code;

    FactType(String code) {
        this.code = code;
    }

    /** Returns the external dot-notation code stored in the ledger and exposed in the API. */
    public String code() {
        return code;
    }

    /**
     * Resolves a dot-notation code to its enum constant.
     *
     * @param code the dot-notation code, e.g. {@code "evento.confirmado"}
     * @return the matching constant, or empty if the code is unknown (→ 422 at ingestion)
     */
    public static Optional<FactType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (FactType ft : values()) {
            if (ft.code.equals(code)) {
                return Optional.of(ft);
            }
        }
        return Optional.empty();
    }

    /** Returns {@code true} if the given dot-notation code belongs to the active vocabulary. */
    public static boolean isKnown(String code) {
        return fromCode(code).isPresent();
    }
}
