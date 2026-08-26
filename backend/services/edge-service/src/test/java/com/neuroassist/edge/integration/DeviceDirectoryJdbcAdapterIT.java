package com.neuroassist.edge.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.edge.application.port.out.DeviceDirectoryPort;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * C-18 — o índice global de dispositivos contra um Postgres real.
 *
 * <p>O ponto do teste não é CRUD: é provar que as consultas funcionam <b>sem tenant vinculado</b>.
 * O {@code MultiTenantConnectionProvider} roteia o {@code search_path} pelo tenant; sem tenant, o
 * {@code search_path} não aponta para schema de tenant nenhum — e é exatamente nessa situação que a
 * verificação de credencial vinda do ACS chega. Só funciona porque o SQL qualifica {@code
 * neuroassist_global.device_directory} explicitamente.
 *
 * <p>Também trava o {@code ?::uuid}: {@code tenant_id} é {@code uuid} na tabela e {@code String} no
 * port — sem o cast, o driver reclama de tipo.
 */
class DeviceDirectoryJdbcAdapterIT extends BaseIntegrationTest {

    // Espelha V0701__edge_device_directory.sql (flyway fica desabilitado no profile de teste).
    private static final String DDL =
            """
            CREATE SCHEMA IF NOT EXISTS neuroassist_global;
            CREATE TABLE IF NOT EXISTS neuroassist_global.device_directory (
                client_id            varchar(120) NOT NULL,
                tenant_id            uuid         NOT NULL,
                dispositivo_edge_id  uuid         NOT NULL,
                created_at           timestamptz  NOT NULL DEFAULT now(),
                PRIMARY KEY (client_id)
            );
            """;

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final String OUTRO_TENANT = "e2e00000-0000-0000-0000-000000000002";

    @Autowired private DeviceDirectoryPort sut;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepare() {
        // Sem tenant vinculado — é assim que a chamada do ACS chega.
        TenantContextHolder.clear();
        jdbcTemplate.execute(DDL);
        jdbcTemplate.execute("TRUNCATE neuroassist_global.device_directory");
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("publish + resolveTenant funcionam sem tenant vinculado")
    void publishAndResolveWithoutBoundTenant() {
        UUID deviceId = UUID.randomUUID();
        String clientId = "edge-" + deviceId;

        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();

        sut.publish(clientId, TENANT, deviceId);

        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
        assertThat(sut.resolveTenant(clientId)).contains(TENANT);
    }

    @Test
    @DisplayName("resolveTenant de clientId desconhecido devolve vazio (nunca explode)")
    void resolveUnknownClientId() {
        assertThat(sut.resolveTenant("edge-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("publish é idempotente: re-enroll do mesmo clientId re-aponta o ponteiro")
    void publishIsIdempotent() {
        UUID deviceId = UUID.randomUUID();
        String clientId = "edge-" + deviceId;

        sut.publish(clientId, TENANT, deviceId);
        sut.publish(clientId, OUTRO_TENANT, deviceId);

        assertThat(sut.resolveTenant(clientId)).contains(OUTRO_TENANT);
        Integer rows =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM neuroassist_global.device_directory"
                                + " WHERE client_id = ?",
                        Integer.class,
                        clientId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("revoke remove o ponteiro; revoke de inexistente é no-op")
    void revokeRemovesPointer() {
        UUID deviceId = UUID.randomUUID();
        String clientId = "edge-" + deviceId;

        sut.publish(clientId, TENANT, deviceId);
        sut.revoke(clientId);

        assertThat(sut.resolveTenant(clientId)).isEmpty();
        sut.revoke(clientId); // no-op, não lança
    }

    @Test
    @DisplayName("o índice guarda só o ponteiro — nenhuma coluna de segredo (C-18 §7.4)")
    void directoryHoldsPointerOnly() {
        var columns =
                jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_schema = 'neuroassist_global'"
                                + " AND table_name = 'device_directory'",
                        String.class);
        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "client_id", "tenant_id", "dispositivo_edge_id", "created_at");
    }
}
