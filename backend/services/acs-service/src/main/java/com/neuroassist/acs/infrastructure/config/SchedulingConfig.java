package com.neuroassist.acs.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendamento do Spring (E2E-031).
 *
 * <p>Sem isto o método {@code @Scheduled} do {@code HistAuditOutboxScheduler} <b>nunca é
 * invocado</b>: o bean é criado, o contexto sobe, nada falha — e o outbox acumula em silêncio.
 * Seria a mesma classe de defeito que a issue existe para eliminar, só que um nível acima: uma fila
 * durável que ninguém drena.
 *
 * <p>É por isso que o pac-service tem uma classe idêntica com um javadoc idêntico.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
