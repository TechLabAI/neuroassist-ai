package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import com.neuroassist.gest.domain.model.Sensibilidade;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfileJpaRepository;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfilePrimitiveJpaRepository;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link
 * com.neuroassist.gest.infrastructure.persistence.adapter.DetectionProfileRepositoryAdapter} and
 * {@link
 * com.neuroassist.gest.infrastructure.persistence.adapter.DetectionProfilePrimitiveRepositoryAdapter}.
 * Uses Testcontainers PostgreSQL (singleton container from {@link BaseIntegrationTest}). Implements
 * GEST-012 acceptance criteria: P3.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class DetectionProfileRepositoryAdapterIT extends BaseIntegrationTest {

    private static final String TEST_TENANT = "emp_test";

    private final DetectionProfileRepositoryPort profilePort;

    private final DetectionProfilePrimitiveRepositoryPort primitivePort;

    private final TipoGestoRepositoryPort tipoGestoPort;

    private final DetectionProfileJpaRepository profileJpaRepository;

    private final DetectionProfilePrimitiveJpaRepository primitiveJpaRepository;

    /**
     * O tenant precisa estar ligado ANTES de a transação começar.
     *
     * <p>{@code @BeforeEach} do JUnit roda <b>depois</b> de o Spring abrir a transação do teste, e
     * o Hibernate fixa o identificador de tenant da Session no instante em que ela é criada — no
     * começo da transação. Nos testes sem {@code @Transactional} isso não aparece, porque cada
     * chamada de porta abre a própria Session já com o contexto ligado. No teste transacional a
     * Session nascia com o fallback {@code neuroassist_global}, onde as tabelas do GEST não
     * existem, e a falha vinha como {@code relation "tipo_gesto" does not exist} — ausência de
     * roteamento disfarçada de schema quebrado.
     *
     * <p>É a mesma armadilha registrada no CLAUDE.md ("nunca resolva o tenant dentro de um método
     * {@code @Transactional}"), do lado do teste. {@code @BeforeTransaction} é o único gancho que
     * roda antes do início da transação.
     */
    @BeforeTransaction
    void setTenantContextBeforeTransaction() {
        TenantContextHolder.set(TEST_TENANT);
    }

    /** Para os testes não transacionais, onde {@code @BeforeTransaction} não é invocado. */
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
        DetectionThresholdTable params = DetectionThresholdTable.defaults();

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
        assertThat(found.get().thresholds()).isEqualTo(DetectionThresholdTable.defaults());
        // A faixa media reproduz, valor por valor, o que o agente usava como constante.
        assertThat(found.get().thresholds().media().earClose()).isEqualTo(0.22);
    }

    // deactivateCurrent() is a bare @Modifying query with no @Transactional of its own by design
    // (RULE-03 — transactions belong only in application.service); calling it directly from a test,
    // with no service layer in between, needs its own transaction to execute in.
    @Test
    @Transactional
    void deactivateCurrent_thenSaveNew_onlyOneActiveProfile() {
        DetectionThresholdTable params = DetectionThresholdTable.defaults();
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

    /**
     * A leitura por versão tem de achar o perfil <b>desativado</b> — é para isso que ela existe. Um
     * relatório de gesto cita a versão com que foi detectado, e a essa altura ela quase sempre já
     * não é a ativa.
     */
    @Test
    @Transactional
    void findByVersion_findsDeactivatedProfile() {
        DetectionThresholdTable params = DetectionThresholdTable.defaults();
        TipoGesto supported = createSupportedPrimitive("Piscar Duplo IT", "ref/piscar-duplo-v1");

        DetectionProfile antigo =
                DetectionProfile.publish(
                        30L, "v1.1.0", params, List.of(supported), TEST_TENANT, "actor");
        profilePort.save(antigo);
        profilePort.deactivateCurrent();

        DetectionProfile atual =
                DetectionProfile.publish(
                        31L, "v1.1.0", params, List.of(supported), TEST_TENANT, "actor");
        profilePort.save(atual);

        Optional<DetectionProfile> found = profilePort.findByVersion(30L);

        assertThat(found).isPresent();
        assertThat(found.get().version()).isEqualTo(30L);
        assertThat(found.get().active()).isFalse();
        assertThat(found.get().thresholds()).isEqualTo(DetectionThresholdTable.defaults());
        // E a versão ativa continua sendo a outra — a consulta não confunde as duas.
        assertThat(profilePort.findByVersion(31L))
                .get()
                .extracting(DetectionProfile::active)
                .isEqualTo(true);
    }

    @Test
    void findByVersion_unknownVersion_returnsEmpty() {
        assertThat(profilePort.findByVersion(9_999L)).isEmpty();
    }

    @Test
    void saveAll_primitives_thenFindByProfileId() {
        TipoGesto supported = createSupportedPrimitive("Sorrir IT", "ref/sorrir-v1");

        DetectionThresholdTable params = DetectionThresholdTable.defaults();
        DetectionProfile profile =
                DetectionProfile.publish(
                        20L, "v1.0.0", params, List.of(supported), TEST_TENANT, "actor");
        DetectionProfile savedProfile = profilePort.save(profile);

        DetectionProfilePrimitive primitive =
                DetectionProfilePrimitive.fromSupported(
                        savedProfile.id(), supported, Sensibilidade.alta);
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
