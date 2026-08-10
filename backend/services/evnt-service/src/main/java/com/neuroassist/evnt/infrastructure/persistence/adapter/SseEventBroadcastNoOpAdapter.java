package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.SseEventBroadcastPort;
import com.neuroassist.evnt.domain.model.Evento;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class SseEventBroadcastNoOpAdapter implements SseEventBroadcastPort {

    @Override
    public void broadcast(Evento evento) {
        // PoC: SSE não implementado nesta fase
    }
}
