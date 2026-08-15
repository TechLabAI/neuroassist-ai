package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * Integration tests for {@link
 * com.neuroassist.gest.infrastructure.persistence.adapter.TipoGestoRepositoryAdapter}. Uses
 * Testcontainers PostgreSQL (singleton container from {@link BaseIntegrationTest}). Schema routing
 * is exercised by setting {@link TenantContextHolder} before each test. Implements GEST-011
 * acceptance criteria: P3.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class TipoGestoRepositoryAdapterIT extends BaseIntegrationTest {

    private static final String TEST_TENANT = "emp_test";

    private final TipoGestoRepositoryPort tipoGestoPort;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.set(TEST_TENANT);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void save_thenFindById_returnsCorrectState() {
        TipoGesto tg = TipoGesto.register("Piscar Olho", TEST_TENANT);

        TipoGesto saved = tipoGestoPort.save(tg);
        Optional<TipoGesto> found = tipoGestoPort.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().nome()).isEqualTo("Piscar Olho");
        assertThat(found.get().detectabilityState()).isEqualTo(DetectabilityState.REGISTERED);
        assertThat(found.get().id()).isEqualTo(saved.id());
    }

    @Test
    void save_preservesAllFields() {
        TipoGesto tg = TipoGesto.register("Levantar Sobrancelha", TEST_TENANT);

        TipoGesto saved = tipoGestoPort.save(tg);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.nome()).isEqualTo("Levantar Sobrancelha");
        assertThat(saved.detectabilityState()).isEqualTo(DetectabilityState.REGISTERED);
        assertThat(saved.engineFeatureRef()).isNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isNotNull();
    }

    @Test
    void existsByNome_duplicateNotDeprecated_returnsTrue() {
        tipoGestoPort.save(TipoGesto.register("Sorrir Levemente", TEST_TENANT));

        boolean exists =
                tipoGestoPort.existsByNomeIgnoreCaseAndStateNotDeprecated("sorrir levemente");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByNome_differentCase_returnsTrue() {
        tipoGestoPort.save(TipoGesto.register("Virar Cabeca", TEST_TENANT));

        boolean exists = tipoGestoPort.existsByNomeIgnoreCaseAndStateNotDeprecated("VIRAR CABECA");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByNome_unknownName_returnsFalse() {
        boolean exists =
                tipoGestoPort.existsByNomeIgnoreCaseAndStateNotDeprecated(
                        "GestoQueNaoExiste_" + System.nanoTime());

        assertThat(exists).isFalse();
    }

    @Test
    void findAllByTenant_returnsSavedItems() {
        tipoGestoPort.save(TipoGesto.register("Franzir Testa", TEST_TENANT));

        List<TipoGesto> all = tipoGestoPort.findAllByTenant();

        assertThat(all).isNotEmpty();
        assertThat(all).anyMatch(g -> g.nome().equals("Franzir Testa"));
    }
}
