package com.neuroassist.trei.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Carries the current user's data-scope restriction. A {@code tipo} of null means "no restriction"
 * (e.g. GERENTE seeing all of the {@code empresa}). Otherwise {@code valores} contains the ids the
 * user is allowed to see for that tipo.
 *
 * <p>Resolved from {@code usuario_empresa.data_scope} at access-decision time and bound to the
 * request via {@code AccessContextHolder}.
 */
public record DataScope(String tipo, List<String> valores) {

    public static DataScope unrestricted() {
        return new DataScope(null, List.of());
    }

    public boolean isUnrestricted() {
        return tipo == null;
    }

    public List<UUID> valoresUuid() {
        return valores.stream().map(UUID::fromString).toList();
    }
}
