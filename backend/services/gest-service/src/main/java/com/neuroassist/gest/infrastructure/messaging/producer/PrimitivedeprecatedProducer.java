package com.neuroassist.gest.infrastructure.messaging.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!poc")
@RequiredArgsConstructor
public class PrimitivedeprecatedProducer {

    private static final String DESTINATION = "com.neuroassist.gest.event.PrimitiveDeprecated";

    private final JmsTemplate jmsTemplate;

    public void publish(Object event) {
        log.info("[JMS] Publishing {} to {}", event.getClass().getSimpleName(), DESTINATION);
        jmsTemplate.convertAndSend(DESTINATION, event);
    }
}
