package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
public class NotificationDispatchLogAdapter implements NotificationDispatchPort {

    @Override
    public void dispatch(NotificationDispatch d) {
        log.info(
                "[ALERTA PoC] tier={} caregiver={} evento={}",
                d.tier(),
                d.caregiverId(),
                d.eventoId());
    }
}
