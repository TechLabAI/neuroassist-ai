package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.Evento;

public interface SseEventBroadcastPort {

    void broadcast(Evento evento);
}
