package com.neuroassist.edge.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Índice global {@code client_id → tenant} para verificação de credencial de dispositivo (C-18,
 * service-identity.md §7.4).
 *
 * <p>{@code POST /internal/devices/verify-credential} recebe do ACS apenas {@code clientId} e
 * {@code clientSecret}. A credencial autoritativa vive em {@code <tenant>.dispositivo_credential} —
 * e o {@code clientId} sozinho não diz qual schema consultar. É o mesmo ovo-e-galinha de C-21,
 * resolvido do mesmo jeito: uma tabela em {@code neuroassist_global} sempre alcançável, com ou sem
 * tenant vinculado.
 *
 * <p><b>Ponteiro apenas.</b> O hash do segredo nunca é publicado aqui; ele permanece no schema do
 * tenant.
 *
 * <p>RULE-01/02: sem imports de Spring ou JPA aqui.
 */
public interface DeviceDirectoryPort {

    /**
     * Publica o ponteiro no índice global. Chamado no enroll, na mesma transação que grava a
     * credencial no schema do tenant.
     */
    void publish(String clientId, String tenantId, UUID dispositivoEdgeId);

    /**
     * Resolve o tenant de um {@code clientId}. Chamado **antes** de abrir qualquer transação — o
     * Hibernate fixa o {@code search_path} ao adquirir a conexão, então vincular o tenant depois
     * não re-rotearia nada (mesma armadilha de C-21).
     *
     * <p>{@code tenantId} é {@code String} em todo o edge-service — o cast para {@code uuid} fica
     * no SQL do adapter, não no domínio.
     *
     * @return o tenant, ou vazio se o {@code clientId} é desconhecido
     */
    Optional<String> resolveTenant(String clientId);

    /** Remove o ponteiro quando o dispositivo é aposentado e a credencial é revogada. */
    void revoke(String clientId);
}
