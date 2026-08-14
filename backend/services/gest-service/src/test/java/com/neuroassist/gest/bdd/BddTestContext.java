package com.neuroassist.gest.bdd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-scenario shared state container for BDD step definitions.
 *
 * <p>Scoped to {@code "cucumber-glue"}: a fresh instance is created for each Cucumber scenario and
 * destroyed when the scenario completes. Singleton beans (JdbcTemplate, MockMvc) are injected
 * normally since shorter-scoped beans may depend on longer-scoped ones.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>HTTP interaction state: {@code lastStatus}, {@code lastBody}
 *   <li>Auth state: {@code currentToken}, {@code currentTenant}
 *   <li>TipoGesto tracking: {@code lastTipoGestoId}, {@code tipoGestoIds}
 *   <li>Profile tracking: {@code lastProfileVersion}
 *   <li>Idempotency helpers: {@code firstEligibilityStatus}, {@code firstEligibilityBody}
 *   <li>JDBC helpers for direct database setup and assertions
 * </ul>
 */
@Component
@Scope("cucumber-glue")
@Getter
@Setter
public class BddTestContext {

    private final JdbcTemplate jdbcTemplate;

    public BddTestContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── Auth state ──────────────────────────────────────────────────────────
    /** Current JWT token (no "Bearer " prefix). */
    private String currentToken;

    /** Current tenant ID (e.g. "emp_test", "emp_other"). */
    private String currentTenant;

    // ── Last HTTP response ───────────────────────────────────────────────────
    private int lastStatus;
    private String lastBody = "";

    // ── TipoGesto tracking ───────────────────────────────────────────────────
    private UUID lastTipoGestoId;
    private final Map<String, UUID> tipoGestoIds = new HashMap<>();

    // ── Detection Profile tracking ───────────────────────────────────────────
    private long lastProfileVersion;

    // ── Eligibility idempotency helpers ──────────────────────────────────────
    private int firstEligibilityStatus;
    private String firstEligibilityBody = "";

    // ── Tenant mapping ───────────────────────────────────────────────────────

    /** Maps a feature-level tenant name to a tenant ID string. */
    public String mapTenantName(String featureName) {
        return switch (featureName) {
            case "empresa-test" -> "emp_test";
            case "empresa-other" -> "emp_other";
            default -> featureName.replace("empresa-", "emp_").replace("-", "_");
        };
    }

    /** Returns the schema name for a tenant ID (e.g. "emp_test" → "t_emp_test"). */
    public String schemaFor(String tenantId) {
        return "t_" + tenantId;
    }

    /** Returns the schema for the currently active tenant. */
    public String currentSchema() {
        return schemaFor(currentTenant);
    }

    // ── TipoGesto tracking helpers ────────────────────────────────────────────

    public void trackTipoGesto(String nome, UUID id) {
        tipoGestoIds.put(nome, id);
        lastTipoGestoId = id;
    }

    public UUID getTipoGestoId(String nome) {
        return tipoGestoIds.get(nome);
    }

    // ── Reset (called by @Before hook) ────────────────────────────────────────

    public void reset() {
        currentToken = null;
        currentTenant = null;
        lastStatus = 0;
        lastBody = "";
        lastTipoGestoId = null;
        tipoGestoIds.clear();
        lastProfileVersion = 0;
        firstEligibilityStatus = 0;
        firstEligibilityBody = "";
    }

    // ── JDBC helpers ──────────────────────────────────────────────────────────

    /** Deletes all GEST data from a schema in FK-safe order. */
    public void cleanSchema(String schema) {
        jdbcTemplate.update("DELETE FROM " + schema + ".tipo_gesto_transition");
        jdbcTemplate.update("DELETE FROM " + schema + ".detection_profile_primitive");
        jdbcTemplate.update("DELETE FROM " + schema + ".processed_events");
        jdbcTemplate.update("DELETE FROM " + schema + ".detection_profile");
        jdbcTemplate.update("DELETE FROM " + schema + ".tipo_gesto");
    }

    /**
     * Inserts a TipoGesto directly into the given schema. Tracks the ID in {@code tipoGestoIds}.
     *
     * @param nome primitive name
     * @param state detectability state string (e.g. "registered")
     * @param engineFeatureRef may be null (allowed for non-supported states)
     * @param schema schema-qualified prefix (e.g. "t_emp_test")
     * @return the UUID assigned to the new row
     */
    public UUID insertTipoGesto(String nome, String state, String engineFeatureRef, String schema) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO "
                        + schema
                        + ".tipo_gesto"
                        + " (id, nome, detectability_state, engine_feature_ref, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, 'bdd-setup', 'bdd-setup')",
                id,
                nome,
                state,
                engineFeatureRef);
        trackTipoGesto(nome, id);
        return id;
    }

    /** Updates the engine_feature_ref of an existing TipoGesto. */
    public void updateEngineFeatureRef(UUID id, String engineFeatureRef, String schema) {
        jdbcTemplate.update(
                "UPDATE " + schema + ".tipo_gesto SET engine_feature_ref = ? WHERE id = ?",
                engineFeatureRef,
                id);
    }

    /** Queries the detectability_state of a TipoGesto by name from the current tenant schema. */
    public String getStateFromDb(String nome) {
        return jdbcTemplate.queryForObject(
                "SELECT detectability_state FROM "
                        + currentSchema()
                        + ".tipo_gesto WHERE lower(nome) = lower(?)",
                String.class,
                nome);
    }

    /** Counts transition records for a TipoGesto ID in the current tenant schema. */
    public int countTransitions(UUID tipoGestoId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + currentSchema()
                                + ".tipo_gesto_transition WHERE tipo_gesto_id = ?",
                        Integer.class,
                        tipoGestoId);
        return count != null ? count : 0;
    }

    /** Counts transition records with the given from/to state for a TipoGesto ID. */
    public int countTransitions(UUID tipoGestoId, String fromState, String toState) {
        String fromClause = fromState == null ? "from_state IS NULL" : "from_state = ?";
        if (fromState == null) {
            Integer count =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM "
                                    + currentSchema()
                                    + ".tipo_gesto_transition"
                                    + " WHERE tipo_gesto_id = ? AND "
                                    + fromClause
                                    + " AND to_state = ?",
                            Integer.class,
                            tipoGestoId,
                            toState);
            return count != null ? count : 0;
        }
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + currentSchema()
                                + ".tipo_gesto_transition"
                                + " WHERE tipo_gesto_id = ? AND from_state = ? AND to_state = ?",
                        Integer.class,
                        tipoGestoId,
                        fromState,
                        toState);
        return count != null ? count : 0;
    }

    /**
     * Inserts a detection_profile row and returns the profile UUID.
     *
     * @param version profile version number
     * @param active whether this profile is the active one
     * @param schema schema name (e.g. "t_emp_test")
     */
    public UUID insertDetectionProfile(long version, boolean active, String schema) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO "
                        + schema
                        + ".detection_profile"
                        + " (id, version, active, contract_version, classification_params)"
                        + " VALUES (?, ?, ?, '1.0.0', '{}'::jsonb)",
                id,
                version,
                active);
        return id;
    }

    /** Inserts a detection_profile_primitive row linking a profile to a TipoGesto snapshot. */
    public void insertDetectionProfilePrimitive(
            UUID profileId,
            UUID tipoGestoId,
            String nomeSnapshot,
            String engineFeatureRef,
            String schema) {
        jdbcTemplate.update(
                "INSERT INTO "
                        + schema
                        + ".detection_profile_primitive"
                        + " (id, detection_profile_id, tipo_gesto_id, nome_snapshot, engine_feature_ref)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                profileId,
                tipoGestoId,
                nomeSnapshot,
                engineFeatureRef);
    }

    /** Counts active profiles in the given schema. */
    public int countActiveProfiles(String schema) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + schema + ".detection_profile WHERE active = true",
                        Integer.class);
        return count != null ? count : 0;
    }

    /** Checks if a profile at the given version has the given active status. */
    public boolean isProfileActive(long version, String schema) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + schema
                                + ".detection_profile WHERE version = ? AND active = true",
                        Integer.class,
                        version);
        return count != null && count > 0;
    }

    /** Returns the count of primitives snapshot for a profile version in the given schema. */
    public int countProfilePrimitives(long version, String schema) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + schema
                                + ".detection_profile_primitive dpp"
                                + " JOIN "
                                + schema
                                + ".detection_profile dp ON dp.id = dpp.detection_profile_id"
                                + " WHERE dp.version = ?",
                        Integer.class,
                        version);
        return count != null ? count : 0;
    }

    /**
     * Checks if a detection_profile_primitive with the given nome_snapshot exists for a version.
     */
    public boolean profileHasPrimitive(long version, String nomeSnapshot, String schema) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + schema
                                + ".detection_profile_primitive dpp"
                                + " JOIN "
                                + schema
                                + ".detection_profile dp ON dp.id = dpp.detection_profile_id"
                                + " WHERE dp.version = ? AND dpp.nome_snapshot = ?",
                        Integer.class,
                        version,
                        nomeSnapshot);
        return count != null && count > 0;
    }

    /** Checks whether any row in processed_events or any table contains the reportId. */
    public boolean anyRecordWithReportId(String reportId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + currentSchema()
                                + ".processed_events WHERE event_id::text = ?",
                        Integer.class,
                        reportId);
        return count != null && count > 0;
    }
}
