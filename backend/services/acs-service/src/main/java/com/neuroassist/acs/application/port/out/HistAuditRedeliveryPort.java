package com.neuroassist.acs.application.port.out;

import java.util.UUID;

/**
 * Outbound port — reentrega ao hist-service um payload guardado no outbox, verbatim (E2E-031).
 *
 * <p>Recebe o JSON gravado em vez de um objeto de domínio: a entrada foi serializada no momento em
 * que o fato aconteceu, e re-derivá-la depois arriscaria enviar algo sutilmente diferente do que a
 * tentativa original enviou. O {@code factId} acima de tudo tem de ser idêntico — é o que faz o
 * hist-service deduplicar em vez de acrescentar o fato uma segunda vez (HIST ADR-005, defeito C-5).
 *
 * <p>O {@code tenantId} vem separado porque não é do corpo da requisição: ele escopa o <b>token</b>
 * da chamada. O hist-service tira o tenant do claim do JWT, então entregar com o token errado
 * gravaria o fato na empresa errada.
 */
public interface HistAuditRedeliveryPort {

    /**
     * @return {@code true} quando o hist-service aceitou o fato
     */
    boolean redeliver(UUID tenantId, String payloadJson);
}
