package com.neuroassist.acs.application.port.in;

/**
 * Inbound port — reentrega fatos de auditoria presos no outbox (E2E-031).
 *
 * <p>Sem parâmetro de tenant, e isso é a diferença em relação aos drenos do PAC e do EVNT: a tabela
 * é global e cada linha diz a que empresa pertence. Não há {@code search_path} a vincular, nem
 * catálogo de tenants a percorrer.
 */
public interface HistAuditOutboxDrainUseCase {

    DrainResult drain();

    /**
     * @param attempted entradas lidas neste tick
     * @param delivered entradas que o hist-service aceitou
     * @param givenUp entradas movidas para {@code failed}
     */
    record DrainResult(int attempted, int delivered, int givenUp) {}
}
