package com.neuroassist.edge.infrastructure.messaging.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispositivopareadoProducer {

    private static final String DESTINATION = "com.neuroassist.edge.event.DispositivoPareado";

    private final JmsTemplate jmsTemplate;

    public void publish(Object event) {
        log.info("[JMS] Publishing {} to {}", event.getClass().getSimpleName(), DESTINATION);
        jmsTemplate.convertAndSend(DESTINATION, event);
    }
}
