package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.EnrollmentDirectoryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter do índice global de enrollment (C-21).
 *
 * <p>Usa {@link JdbcTemplate} em vez de JPA de propósito: o {@code MultiTenantConnectionProvider}
 * roteia o {@code search_path} pelo tenant vinculado, e este índice precisa ser legível justamente
 * quando **não há** tenant — que é a situação do enroll anônimo. As consultas qualificam o schema
 * explicitamente ({@code neuroassist_global.enrollment_directory}), então funcionam com qualquer
 * {@code search_path}.
 *
 * <p>RULE-06/04/05: infraestrutura, sem dependência de {@code api.*}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentDirectoryJdbcAdapter implements EnrollmentDirectoryPort {

    private static final String TABLE = "neuroassist_global.enrollment_directory";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void publish(
            String tokenHash, String tenantId, UUID dispositivoEdgeId, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO "
                        + TABLE
                        + " (token_hash, tenant_id, dispositivo_edge_id, expires_at)"
                        + " VALUES (?, ?::uuid, ?, ?)"
                        + " ON CONFLICT (token_hash) DO UPDATE SET"
                        + " tenant_id = EXCLUDED.tenant_id,"
                        + " dispositivo_edge_id = EXCLUDED.dispositivo_edge_id,"
                        + " expires_at = EXCLUDED.expires_at",
                tokenHash,
                tenantId,
                dispositivoEdgeId,
                Timestamp.from(expiresAt));
        log.debug(
                "Enrollment directory: ponteiro publicado device={} tenant={}",
                dispositivoEdgeId,
                tenantId);
    }

    @Override
    public Optional<String> resolveTenant(String tokenHash) {
        var rows =
                jdbcTemplate.query(
                        "SELECT tenant_id FROM " + TABLE + " WHERE token_hash = ?",
                        (rs, i) -> rs.getString("tenant_id"),
                        tokenHash);
        return rows.stream().findFirst();
    }

    @Override
    public void revoke(String tokenHash) {
        int removed =
                jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE token_hash = ?", tokenHash);
        if (removed > 0) {
            log.debug("Enrollment directory: ponteiro revogado");
        }
    }
}
