package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DeviceDirectoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter do índice global de dispositivos (C-18).
 *
 * <p>Usa {@link JdbcTemplate} em vez de JPA de propósito, pelo mesmo motivo do {@link
 * EnrollmentDirectoryJdbcAdapter}: o {@code MultiTenantConnectionProvider} roteia o {@code
 * search_path} pelo tenant vinculado, e este índice precisa ser legível justamente quando **não
 * há** tenant — que é a situação da verificação de credencial vinda do ACS. As consultas qualificam
 * o schema explicitamente ({@code neuroassist_global.device_directory}), então funcionam com
 * qualquer {@code search_path}.
 *
 * <p>RULE-04/05: infraestrutura, sem dependência de {@code api.*}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDirectoryJdbcAdapter implements DeviceDirectoryPort {

    private static final String TABLE = "neuroassist_global.device_directory";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void publish(String clientId, String tenantId, UUID dispositivoEdgeId) {
        jdbcTemplate.update(
                "INSERT INTO "
                        + TABLE
                        + " (client_id, tenant_id, dispositivo_edge_id)"
                        + " VALUES (?, ?::uuid, ?)"
                        + " ON CONFLICT (client_id) DO UPDATE SET"
                        + " tenant_id = EXCLUDED.tenant_id,"
                        + " dispositivo_edge_id = EXCLUDED.dispositivo_edge_id",
                clientId,
                tenantId,
                dispositivoEdgeId);
        log.debug(
                "Device directory: ponteiro publicado device={} tenant={}",
                dispositivoEdgeId,
                tenantId);
    }

    @Override
    public Optional<String> resolveTenant(String clientId) {
        var rows =
                jdbcTemplate.query(
                        "SELECT tenant_id FROM " + TABLE + " WHERE client_id = ?",
                        (rs, i) -> rs.getString("tenant_id"),
                        clientId);
        return rows.stream().findFirst();
    }

    @Override
    public void revoke(String clientId) {
        int removed =
                jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE client_id = ?", clientId);
        if (removed > 0) {
            log.debug("Device directory: ponteiro revogado clientId='{}'", clientId);
        }
    }
}
