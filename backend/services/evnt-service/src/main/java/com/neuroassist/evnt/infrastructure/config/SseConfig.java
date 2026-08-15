package com.neuroassist.evnt.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled support for SseEmitterAdapter.keepAlive() (EVNT-014). Required
 * because EvntApplication does not carry @EnableScheduling at the bootstrap level.
 */
@Configuration
@EnableScheduling
public class SseConfig {}
