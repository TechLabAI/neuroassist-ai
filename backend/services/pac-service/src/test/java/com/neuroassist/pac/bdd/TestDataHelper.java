package com.neuroassist.pac.bdd;

import com.neuroassist.pac.infrastructure.persistence.entity.ComandoJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.ComandoJpaRepository;
import com.neuroassist.pac.infrastructure.persistence.repository.PacienteJpaRepository;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Direct JPA test data helper for BDD {@code Given} steps (PAC-025, P9).
 *
 * <p>Bypasses the service layer for test fixture setup. {@code When} steps use the REST API. {@code
 * Given} steps use this helper to insert data directly — this is the accepted pattern for BDD
 * integration tests (arrange-act-assert; only the act phase exercises the real API).
 *
 * <p>RULE-03: no {@code @Transactional} here — transactions managed via {@link TransactionTemplate}
 * to ensure tenant context is honoured.
 *
 * <p>Not annotated with @Component — registered exclusively via {@link BddTestConfiguration}.
 */
@RequiredArgsConstructor
public class TestDataHelper {

    private final PacienteJpaRepository pacienteRepo;
    private final ComandoJpaRepository comandoRepo;
    private final TransactionTemplate transactionTemplate;

    // -------------------------------------------------------------------------
    // Paciente
    // -------------------------------------------------------------------------

    /**
     * Creates a Paciente in the given empresa schema and returns its UUID.
     *
     * @param alias human-readable alias (used for logging)
     * @param empresaId tenant identifier (set as search_path + stored in tenantId column)
     * @return UUID assigned by the database
     */
    public UUID createPaciente(String alias, String empresaId) {
        TenantContextHolder.set(empresaId);
        try {
            return transactionTemplate.execute(
                    status -> {
                        PacienteJpaEntity entity =
                                PacienteJpaEntity.builder()
                                        .tenantId(empresaId)
                                        .nome("Test Patient " + alias)
                                        .rowVersion(0)
                                        .catalogoVersao(0)
                                        .createdAt(Instant.now())
                                        .updatedAt(Instant.now())
                                        .build();
                        return pacienteRepo.save(entity).getId();
                    });
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Creates a Paciente with a specific {@code catalogoVersao} value.
     *
     * @param alias human-readable alias
     * @param empresaId tenant identifier
     * @param catalogoVersao catalog version to set
     * @return UUID assigned by the database
     */
    public UUID createPacienteWithCatalogoVersao(
            String alias, String empresaId, int catalogoVersao) {
        TenantContextHolder.set(empresaId);
        try {
            return transactionTemplate.execute(
                    status -> {
                        PacienteJpaEntity entity =
                                PacienteJpaEntity.builder()
                                        .tenantId(empresaId)
                                        .nome("Test Patient " + alias)
                                        .rowVersion(0)
                                        .catalogoVersao(catalogoVersao)
                                        .createdAt(Instant.now())
                                        .updatedAt(Instant.now())
                                        .build();
                        return pacienteRepo.save(entity).getId();
                    });
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Creates a Paciente with a specific {@code rowVersion}.
     *
     * @param empresaId tenant identifier
     * @param rowVersion row version to set (for ETag testing)
     * @return UUID assigned by the database
     */
    public UUID createPacienteWithRowVersion(String empresaId, int rowVersion) {
        TenantContextHolder.set(empresaId);
        try {
            return transactionTemplate.execute(
                    status -> {
                        PacienteJpaEntity entity =
                                PacienteJpaEntity.builder()
                                        .tenantId(empresaId)
                                        .nome("Test Patient with rowVersion " + rowVersion)
                                        .rowVersion(rowVersion)
                                        .catalogoVersao(0)
                                        .createdAt(Instant.now())
                                        .updatedAt(Instant.now())
                                        .build();
                        return pacienteRepo.save(entity).getId();
                    });
        } finally {
            TenantContextHolder.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Comando
    // -------------------------------------------------------------------------

    /**
     * Creates a Comando directly in the database with the given state.
     *
     * @param pacienteId UUID of the owning Paciente
     * @param empresaId tenant identifier
     * @param estado lifecycle state: "drafted" | "validated" | "published" | "retired"
     * @param urgencia urgency: "rotina" | "emergencia"
     * @param calibracaoVersao calibration version
     * @param primitivoPrincipalId UUID of the principal gesture primitive
     * @param primitivoSecundarioId optional secondary gesture primitive UUID (may be null)
     * @return UUID assigned by the database
     */
    public UUID createComando(
            UUID pacienteId,
            String empresaId,
            String estado,
            String urgencia,
            int calibracaoVersao,
            UUID primitivoPrincipalId,
            UUID primitivoSecundarioId) {
        TenantContextHolder.set(empresaId);
        try {
            return transactionTemplate.execute(
                    status -> {
                        ComandoJpaEntity entity =
                                ComandoJpaEntity.builder()
                                        .pacienteId(pacienteId)
                                        .rotulo("test-rotulo-" + estado)
                                        .necessidade("hidratacao")
                                        .primitivoPrincipalId(primitivoPrincipalId)
                                        .primitivoSecundarioId(primitivoSecundarioId)
                                        .sensibilidade("media")
                                        .tempoMinGestoMs(300)
                                        .tempoMaxGestoMs(1500)
                                        .urgencia(urgencia)
                                        .estado(estado)
                                        .calibracaoVersao(calibracaoVersao)
                                        .rowVersion(0)
                                        .createdAt(Instant.now())
                                        .updatedAt(Instant.now())
                                        .build();
                        return comandoRepo.save(entity).getId();
                    });
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** Convenience: creates a Comando with default primitives (gst-001), rotina urgency. */
    public UUID createComando(UUID pacienteId, String empresaId, String estado) {
        UUID gst001 = UUID.nameUUIDFromBytes("gst-001".getBytes());
        return createComando(pacienteId, empresaId, estado, "rotina", 0, gst001, null);
    }

    /** Convenience: creates a Comando with specific urgency and default primitives. */
    public UUID createComando(UUID pacienteId, String empresaId, String estado, String urgencia) {
        UUID gst001 = UUID.nameUUIDFromBytes("gst-001".getBytes());
        return createComando(pacienteId, empresaId, estado, urgencia, 0, gst001, null);
    }

    /** Convenience: creates a Comando with specific calibracaoVersao. */
    public UUID createComandoWithCalibracao(
            UUID pacienteId, String empresaId, String estado, int calibracaoVersao) {
        UUID gst001 = UUID.nameUUIDFromBytes("gst-001".getBytes());
        return createComando(
                pacienteId, empresaId, estado, "rotina", calibracaoVersao, gst001, null);
    }

    /**
     * Returns a deterministic test UUID for a gesture primitive alias.
     *
     * <p>Maps "gst-001" → fixed UUID, "gst-pending" → different fixed UUID, etc.
     */
    public static UUID primitiveUuid(String alias) {
        return UUID.nameUUIDFromBytes(alias.getBytes());
    }
}
