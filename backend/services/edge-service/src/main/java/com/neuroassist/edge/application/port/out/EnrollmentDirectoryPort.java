package com.neuroassist.edge.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Índice global {@code token_hash → tenant} para o enroll de dispositivo (C-21).
 *
 * <p>{@code POST /api/v1/edge/devices/enroll} é anônimo por desenho — o enrollment token é a
 * credencial (ADR-005). Sem JWT não há claim de tenant, o {@code TenantContextFilter} não vincula
 * nada e o {@code search_path} fica no schema global. Como {@code dispositivo_enrollment} vive no
 * schema do tenant, a consulta falha — e para descobrir o tenant seria preciso lê-la primeiro.
 *
 * <p>Este port quebra o ciclo: a tabela {@code neuroassist_global.enrollment_directory} é sempre
 * alcançável, com ou sem tenant vinculado. Guarda apenas o ponteiro; o registro autoritativo
 * continua em {@code <tenant>.dispositivo_enrollment}.
 *
 * <p>RULE-01/02: sem imports de Spring ou JPA aqui.
 */
public interface EnrollmentDirectoryPort {

    /**
     * Publica o ponteiro no índice global. Chamado no registro do dispositivo, na mesma transação
     * que grava o enrollment no schema do tenant.
     */
    void publish(String tokenHash, String tenantId, UUID dispositivoEdgeId, Instant expiresAt);

    /**
     * Resolve o tenant de um enrollment token. Chamado **antes** de abrir a transação do enroll —
     * setar o tenant depois não re-rotearia a conexão já adquirida pelo Hibernate.
     *
     * <p>{@code tenantId} é {@code String} em todo o edge-service — o cast para {@code uuid} fica
     * no SQL do adapter, não no domínio.
     *
     * @return o tenant, ou vazio se o token é desconhecido (não distingue de expirado: o {@code
     *     enroll} valida expiração e consumo contra o registro autoritativo)
     */
    Optional<String> resolveTenant(String tokenHash);

    /** Remove o ponteiro quando o enrollment é consumido ou o dispositivo é aposentado. */
    void revoke(String tokenHash);
}
