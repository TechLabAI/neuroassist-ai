package com.neuroassist.pac.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduling support, required by {@code HistOutboxScheduler}.
 *
 * <p>Without this the {@code @Scheduled} method is silently never invoked — the bean is created,
 * the annotation is ignored, and nothing in the logs says so. That failure mode is the same shape
 * as the defect the scheduler exists to fix (C-44), so it is worth stating plainly here.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
