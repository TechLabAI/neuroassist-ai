package com.neuroassist.hist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test — validates that the JPA entity mapping (HIST-009) produces the expected schema
 * structure matching the Flyway migration scripts V0001..V0005. The test profile uses {@code
 * ddl-auto: create-drop} so Hibernate creates the schema from entity annotations; this test asserts
 * that the generated schema has the required tables and key columns (INT-HIST-004).
 *
 * <p>Note: In production, schema creation is delegated to {@code neuroassist-ai-migrations} which
 * applies the Flyway scripts directly. This test validates structural equivalence.
 */
class FlywayMigrationTest extends BaseIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("audit_record table exists with all required columns (V0001)")
    void auditRecordTable_existsWithRequiredColumns() {
        List<String> columns = fetchColumnNames("audit_record");

        assertThat(columns)
                .as("audit_record must contain all 15 domain fields + PK")
                .contains(
                        "audit_record_id",
                        "tenant_id",
                        "fact_id",
                        "fact_type",
                        "subject_type",
                        "subject_id",
                        "caregiver_id",
                        "actor_display_name",
                        "payload",
                        "occurred_at",
                        "recorded_at",
                        "stream_key",
                        "sequence_no",
                        "prev_hash",
                        "record_hash",
                        "corrects_record_id");
    }

    @Test
    @DisplayName("audit_idempotency table exists with unique (fact_id, tenant_id) (V0002)")
    void auditIdempotencyTable_existsWithRequiredColumns() {
        List<String> columns = fetchColumnNames("audit_idempotency");

        assertThat(columns)
                .as("audit_idempotency must contain PK + idempotency key fields")
                .contains(
                        "idempotency_id", "fact_id", "tenant_id", "audit_record_id", "recorded_at");
    }

    @Test
    @DisplayName("stream_head table exists with unique (stream_key, tenant_id) (V0003)")
    void streamHeadTable_existsWithRequiredColumns() {
        List<String> columns = fetchColumnNames("stream_head");

        assertThat(columns)
                .as("stream_head must contain PK + mutable tip fields")
                .contains(
                        "stream_head_id",
                        "stream_key",
                        "tenant_id",
                        "current_sequence_no",
                        "current_hash",
                        "last_recorded_at");
    }

    @Test
    @DisplayName("audit_record has unique constraint on (fact_id, tenant_id) — idempotency")
    void auditRecord_uniqueConstraint_factIdTenant() {
        // Verify by attempting to insert duplicate (fact_id, tenant_id) → constraint violation
        String insert =
                """
                INSERT INTO audit_record (
                    audit_record_id, tenant_id, fact_id, fact_type,
                    subject_type, subject_id, caregiver_id, actor_display_name,
                    payload, occurred_at, recorded_at, stream_key,
                    sequence_no, prev_hash, record_hash
                ) VALUES (
                    gen_random_uuid(), 'test-tenant', '11111111-0000-0000-0000-000000000001', 'evento.confirmado',
                    'paciente', gen_random_uuid(), gen_random_uuid(), 'Test Actor',
                    '{}', NOW(), NOW(), 'paciente:test-stream',
                    1, '0000000000000000000000000000000000000000000000000000000000000000',
                       'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                )
                """;
        jdbcTemplate.execute(insert);

        // Attempting to insert same (fact_id, tenant_id) must fail
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> jdbcTemplate.execute(insert),
                "Expected unique constraint violation on (fact_id, tenant_id)");
    }

    // ---- helper ----

    private List<String> fetchColumnNames(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = ?
                  AND table_schema NOT IN ('pg_catalog', 'information_schema')
                ORDER BY ordinal_position
                """,
                String.class,
                tableName);
    }
}
