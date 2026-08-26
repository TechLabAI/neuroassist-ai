package com.neuroassist.mig.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neuroassist.mig.application.port.out.SchemaMigrationPort;
import com.neuroassist.mig.application.port.out.TenantCatalogPort;
import com.neuroassist.mig.domain.model.ProvisioningStatus;
import com.neuroassist.mig.domain.model.TenantSchemaProvisioning;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * E2E-016 — orchestration of tenant provisioning.
 *
 * <p>The property that matters most here is the one the stub violated: {@code READY} must be
 * something this service <i>found out</i>, never something it assumed. Every test below is a way
 * for READY to be wrong.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantSchemaProvisioningService — provisionamento de schema de tenant")
class TenantSchemaProvisioningServiceTest {

    private static final String SCHEMA = "t_ab12cd34ef56";

    @Mock private SchemaMigrationPort schemaMigrationPort;
    @Mock private TenantCatalogPort tenantCatalogPort;

    private TenantSchemaProvisioningService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TenantSchemaProvisioningService(schemaMigrationPort, tenantCatalogPort);
        when(tenantCatalogPort.tenantExists(any())).thenReturn(true);
        // Run the locked block inline; the lock itself is the adapter's contract, not this one's.
        when(schemaMigrationPort.withTenantLock(any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    @Test
    @DisplayName("cria o schema e migra até head")
    void createsAndMigrates() {
        when(schemaMigrationPort.createSchemaIfAbsent(SCHEMA)).thenReturn(true);
        when(schemaMigrationPort.migrateToHead(SCHEMA)).thenReturn(List.of("0200", "0201"));
        when(schemaMigrationPort.currentVersion(SCHEMA)).thenReturn("0209");

        TenantSchemaProvisioning result = service.provision(tenantId, SCHEMA);

        assertThat(result.status()).isEqualTo(ProvisioningStatus.READY);
        assertThat(result.appliedVersions()).containsExactly("0200", "0201");
        assertThat(result.currentVersion()).isEqualTo("0209");
        assertThat(result.alreadyCurrent()).isFalse();
    }

    @Test
    @DisplayName("idempotente: tenant já em head devolve READY sem versões aplicadas")
    void idempotentWhenAlreadyAtHead() {
        when(schemaMigrationPort.createSchemaIfAbsent(SCHEMA)).thenReturn(false);
        when(schemaMigrationPort.migrateToHead(SCHEMA)).thenReturn(List.of());
        when(schemaMigrationPort.currentVersion(SCHEMA)).thenReturn("0209");

        TenantSchemaProvisioning result = service.provision(tenantId, SCHEMA);

        assertThat(result.status()).isEqualTo(ProvisioningStatus.READY);
        assertThat(result.alreadyCurrent()).isTrue();
    }

    @Test
    @DisplayName("tenant fora do registro global é recusado ANTES de qualquer DDL")
    void unknownTenantIsRefusedBeforeAnyDdl() {
        when(tenantCatalogPort.tenantExists(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.provision(tenantId, SCHEMA))
                .isInstanceOf(UnknownTenantException.class);

        // Um schema migrado que nenhum serviço consegue rotear, e sem rastro de quem o pediu.
        verify(schemaMigrationPort, never()).createSchemaIfAbsent(anyString());
        verify(schemaMigrationPort, never()).migrateToHead(anyString());
    }

    @Test
    @DisplayName("nome de schema fora do formato não chega ao DDL")
    void rejectsSchemaNameOutsideTheAllowlist() {
        assertThatThrownBy(() -> service.provision(tenantId, "public"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.provision(tenantId, "t_x\"; DROP SCHEMA public; --"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(schemaMigrationPort);
    }

    @Test
    @DisplayName("falha do Flyway vira migration-failed, nunca READY")
    void migrationFailureNeverReportsReady() {
        when(schemaMigrationPort.createSchemaIfAbsent(SCHEMA)).thenReturn(true);
        when(schemaMigrationPort.migrateToHead(SCHEMA))
                .thenThrow(new RuntimeException("checksum drift"));

        assertThatThrownBy(() -> service.provision(tenantId, SCHEMA))
                .isInstanceOf(SchemaMigrationFailedException.class);
    }

    @Test
    @DisplayName("status: registro aponta schema que não existe → FAILED, nunca READY")
    void statusIsFailedWhenRegistryPointsAtNothing() {
        when(tenantCatalogPort.schemaOf(tenantId)).thenReturn(Optional.of(SCHEMA));
        when(schemaMigrationPort.schemaExists(SCHEMA)).thenReturn(false);

        // Este é exatamente o estado que o stub fabricava a cada onboarding: schema_nome
        // preenchido, schema inexistente, e READY na resposta.
        assertThat(service.statusOf(tenantId)).isEqualTo(ProvisioningStatus.FAILED);
    }

    @Test
    @DisplayName("status: sem schema registrado ainda → PENDING")
    void statusIsPendingWhenNoSchemaRecorded() {
        when(tenantCatalogPort.schemaOf(tenantId)).thenReturn(Optional.empty());

        assertThat(service.statusOf(tenantId)).isEqualTo(ProvisioningStatus.PENDING);
    }

    @Test
    @DisplayName("status: schema existe e tem versão → READY")
    void statusIsReadyWhenSchemaIsMigrated() {
        when(tenantCatalogPort.schemaOf(tenantId)).thenReturn(Optional.of(SCHEMA));
        when(schemaMigrationPort.schemaExists(SCHEMA)).thenReturn(true);
        when(schemaMigrationPort.currentVersion(SCHEMA)).thenReturn("0209");

        assertThat(service.statusOf(tenantId)).isEqualTo(ProvisioningStatus.READY);
    }

    @Test
    @DisplayName("status: schema existe mas sem histórico Flyway → PROVISIONING")
    void statusIsProvisioningWhenSchemaHasNoHistory() {
        when(tenantCatalogPort.schemaOf(tenantId)).thenReturn(Optional.of(SCHEMA));
        when(schemaMigrationPort.schemaExists(SCHEMA)).thenReturn(true);
        when(schemaMigrationPort.currentVersion(SCHEMA)).thenReturn(null);

        assertThat(service.statusOf(tenantId)).isEqualTo(ProvisioningStatus.PROVISIONING);
    }
}
