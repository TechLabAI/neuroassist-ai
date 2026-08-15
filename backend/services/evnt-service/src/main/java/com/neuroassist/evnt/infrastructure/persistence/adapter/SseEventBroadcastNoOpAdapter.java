package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.SseEventBroadcastPort;
import com.neuroassist.evnt.domain.model.Evento;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op fallback adapter — active only when SseEmitterAdapter is NOT in the context (e.g. tests
 * that mock the port). @ConditionalOnMissingBean prevents ambiguous bean conflict with
 * SseEmitterAdapter in production and integration contexts (P8 fix: EVNT-022).
 */
@Component
@ConditionalOnMissingBean(SseEventBroadcastPort.class)
public class SseEventBroadcastNoOpAdapter implements SseEventBroadcastPort {

    @Override
    public void broadcast(Evento evento) {
        // No-op fallback — real delivery handled by SseEmitterAdapter
    }
}
