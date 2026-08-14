package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfileJpaRepository;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfilePrimitiveJpaRepository;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link
 * com.neuroassist.gest.infrastructure.persistence.adapter.DetectionProfileRepositoryAdapter} and
 * {@link
 * com.neuroassist.gest.infrastructure.persistence.adapter.DetectionProfilePrimitiveRepositoryAdapter}.
 * Uses Testcontainers PostgreSQL (singleton container from {@link BaseIntegrationTest}). Implements
 * GEST-012 acceptance criteria: P3.
 */
class DetectionProfileRepositoryAdapterIT extends BaseIntegrationTest {

    private static final String TEST_TENANT = "emp_test";

    private DetectionProfileRepositoryPort profilePort;

    private DetectionProfilePrimitiveRepositoryPort primitivePort;

    private TipoGestoRepositoryPort tipoGestoPort;

    private DetectionProfileJpaRepository profileJpaRepository;

    private DetectionProfilePrimitiveJpaRepository primitiveJpaRepository;

    @Autowired
    void setProfilePort(DetectionProfileRepositoryPort profilePort) {
        this.profilePort = profilePort;
    }

    @Autowired
    void setPrimitivePort(DetectionProfilePrimitiveRepositoryPort primitivePort) {
        this.primitivePort = primitivePort;
    }

    @Autowired
    void setTipoGestoPort(TipoGestoRepositoryPort tipoGestoPort) {
        this.tipoGestoPort = tipoGestoPort;
    }

    @Autowired
    void setProfileJpaRepository(DetectionProfileJpaRepository profileJpaRepository) {
        this.profileJpaRepository = profileJpaRepository;
    }

    @Autowired
    void setPrimitiveJpaRepository(DetectionProfilePrimitiveJpaRepository primitiveJpaRepository) {
        this.primitiveJpaRepository = primitiveJpaRepository;
    }

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(TEST_TENANT);
    }

    /**
     * detection_profile has a partial unique index enforcing a single active=true row per schema
     * (ux_dp_single_active); without cleanup, a profile left behind by one test collides with the
     * next test's own "publish an active profile" insert.
     */
    @AfterEach
    void clearTenantContextAndProfiles() {
        primitiveJpaRepository.deleteAll();
        profileJpaRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    void save_thenFindActive_returnsActiveProfile() {
        Map<String, Object> params = Map.of("confidenceThreshold", 0.85, "minWindowMs", 100);

        // Given: one supported primitive
        TipoGesto supported = createSupportedPrimitive("Piscar Olho IT", "ref/piscar-olho-v1");

        DetectionProfile profile =
                DetectionProfile.publish(
                        1L, "v1.0.0", params, List.of(supported), TEST_TENANT, "test-actor");

        DetectionProfile saved = profilePort.save(profile);
        Optional<DetectionProfile> found = profilePort.findActive();

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.id());
        assertThat(found.get().version()).isEqualTo(1L);
        assertThat(found.get().active()).isTrue();
        assertThat(found.get().contractVersion()).isEqualTo("v1.0.0");
        assertThat(found.get().classificationParams()).containsEntry("confidenceThreshold", 0.85);
    }

    // deactivateCurrent() is a bare @Modifying query with no @Transactional of its own by design
    // (RULE-03 — transactions belong only in application.service); calling it directly from a test,
    // with no service layer in between, needs its own transaction to execute in.
    @Test
    @Transactional
    void deactivateCurrent_thenSaveNew_onlyOneActiveProfile() {
        Map<String, Object> params = Map.of("confidenceThreshold", 0.9);
        TipoGesto supported =
                createSupportedPrimitive("Levantar Sobrancelha IT", "ref/sobrancelha-v1");

        // Persist first active profile
        DetectionProfile first =
                DetectionProfile.publish(
                        10L, "v1.0.0", params, List.of(supported), TEST_TENANT, "actor");
        profilePort.save(first);

        // Deactivate all and save second
        profilePort.deactivateCurrent();

        DetectionProfile second =
                DetectionProfile.publish(
                        11L, "v1.0.0", params, List.of(supported), TEST_TENANT, "actor");
        profilePort.save(second);

        Optional<DetectionProfile> active = profilePort.findActive();
        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(11L);
    }

    @Test
    void saveAll_primitives_thenFindByProfileId() {
        TipoGesto supported = createSupportedPrimitive("Sorrir IT", "ref/sorrir-v1");

        Map<String, Object> params = Map.of("confidenceThreshold", 0.8);
        DetectionProfile profile =
                DetectionProfile.publish(
                        20L, "v1.0.0", params, List.of(supported), TEST_TENANT, "actor");
        DetectionProfile savedProfile = profilePort.save(profile);

        DetectionProfilePrimitive primitive =
                DetectionProfilePrimitive.fromSupported(savedProfile.id(), supported);
        List<DetectionProfilePrimitive> savedPrimitives = primitivePort.saveAll(List.of(primitive));

        List<DetectionProfilePrimitive> found = primitivePort.findByProfileId(savedProfile.id());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).nomeSnapshot()).isEqualTo("Sorrir IT");
        assertThat(found.get(0).engineFeatureRef()).isEqualTo("ref/sorrir-v1");
        assertThat(found.get(0).profileId()).isEqualTo(savedProfile.id());
        assertThat(found.get(0).tipoGestoId()).isEqualTo(supported.id());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Creates and persists a TipoGesto in SUPPORTED state (with engineFeatureRef) so it can be
     * snapshotted into a DetectionProfile.
     */
    private TipoGesto createSupportedPrimitive(String nome, String engineRef) {
        // Build directly as a record — bypassing state-machine for test convenience.
        TipoGesto supported =
                new TipoGesto(
                        UUID.randomUUID(),
                        nome,
                        DetectabilityState.SUPPORTED,
                        engineRef,
                        TEST_TENANT,
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        null,
                        null);
        return tipoGestoPort.save(supported);
    }
}
