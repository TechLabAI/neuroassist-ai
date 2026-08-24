package com.neuroassist.mig.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.mig.application.port.in.MigrateAllUseCase.RunReport;
import com.neuroassist.mig.application.port.in.MigrateAllUseCase.SchemaOutcome;
import com.neuroassist.mig.application.port.out.SchemaMigrationPort;
import com.neuroassist.mig.application.port.out.TenantCatalogPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * E2E-030 — the fan-out, and above all what it does when one schema is broken.
 *
 * <p>"It failed" is not a useful answer to a deploy pipeline. The distinctions asserted here are
 * the ones an operator acts on: which schemas are behind, whether anything was half-applied, and
 * whether the fleet may be rolled out to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MigrateAllService — leque de migrations sobre global + tenants")
class MigrateAllServiceTest {

    @Mock private SchemaMigrationPort schemaMigrationPort;
    @Mock private TenantCatalogPort tenantCatalogPort;

    private MigrateAllService service;

    @BeforeEach
    void setUp() {
        service = new MigrateAllService(schemaMigrationPort, tenantCatalogPort);
        when(schemaMigrationPort.migrateGlobalToHead()).thenReturn(List.of());
        when(schemaMigrationPort.currentGlobalVersion()).thenReturn("0101");
    }

    @Test
    @DisplayName("migra o global e depois cada schema de tenant")
    void migratesGlobalThenEveryTenant() {
        when(tenantCatalogPort.allTenantSchemas()).thenReturn(List.of("t_a", "t_b"));
        when(schemaMigrationPort.migrateToHead(anyString())).thenReturn(List.of("0501"));
        when(schemaMigrationPort.currentVersion(anyString())).thenReturn("0800");

        RunReport report = service.migrateAll();

        assertThat(report.anyFailed()).isFalse();
        assertThat(report.outcomes())
                .extracting(SchemaOutcome::schema)
                .containsExactly("neuroassist_global", "t_a", "t_b");
    }

    @Test
    @DisplayName("global falhou → não abre o leque sobre os tenants")
    void globalFailureStopsTheFanOut() {
        when(schemaMigrationPort.migrateGlobalToHead())
                .thenThrow(new RuntimeException("checksum drift"));

        RunReport report = service.migrateAll();

        assertThat(report.anyFailed()).isTrue();
        // O catálogo de tenants vive no schema global. Percorrer tenants a partir de um catálogo
        // cuja forma acabou de falhar seria ler uma tabela em que não se pode confiar.
        verify(tenantCatalogPort, never()).allTenantSchemas();
        verify(schemaMigrationPort, never()).migrateToHead(anyString());
    }

    @Test
    @DisplayName("um tenant quebrado não impede os outros de convergirem")
    void oneBrokenTenantDoesNotStrandTheOthers() {
        when(tenantCatalogPort.allTenantSchemas()).thenReturn(List.of("t_ok1", "t_ruim", "t_ok2"));
        when(schemaMigrationPort.migrateToHead("t_ok1")).thenReturn(List.of("0501"));
        when(schemaMigrationPort.migrateToHead("t_ruim"))
                .thenThrow(new RuntimeException("relation already exists"));
        when(schemaMigrationPort.migrateToHead("t_ok2")).thenReturn(List.of("0501"));
        when(schemaMigrationPort.currentVersion(anyString())).thenReturn("0800");

        RunReport report = service.migrateAll();

        // Uma empresa com schema quebrado não pode prender todas as outras numa versão antiga.
        verify(schemaMigrationPort).migrateToHead("t_ok2");
        assertThat(report.anyFailed()).isTrue();
        assertThat(report.failures()).extracting(SchemaOutcome::schema).containsExactly("t_ruim");
    }

    @Test
    @DisplayName("catálogo ilegível é pré-flight: nada foi tentado, nada ficou pela metade")
    void unreadableRegistryIsAPreflightFailure() {
        when(tenantCatalogPort.allTenantSchemas())
                .thenThrow(new RuntimeException("relation \"tenant\" does not exist"));

        RunReport report = service.migrateAll();

        // Distinto de falha de migration de propósito: depois de um pré-flight se tenta de novo;
        // depois de uma falha de migration se olha QUAIS schemas ficaram para trás.
        assertThat(report.preflightFailed()).isTrue();
        assertThat(report.anyFailed()).isFalse();
    }

    @Test
    @DisplayName("migrate-global não toca em tenant nenhum")
    void migrateGlobalTouchesNoTenant() {
        RunReport report = service.migrateGlobal();

        assertThat(report.outcomes()).hasSize(1);
        verify(tenantCatalogPort, never()).allTenantSchemas();
    }
}
