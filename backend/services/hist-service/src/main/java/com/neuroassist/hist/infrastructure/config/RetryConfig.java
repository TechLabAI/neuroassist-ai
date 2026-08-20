package com.neuroassist.hist.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry AOP infrastructure for stream-tip contention hardening (HIST-022 / SPEC-05).
 *
 * <p>{@code @EnableRetry} activates the {@code @Retryable} interceptor. The interceptor is ordered
 * to wrap around the transaction interceptor so that each retry attempt starts a fresh transaction
 * (required for pessimistic-lock replay semantics).
 *
 * <p>Retry parameters — {@code maxAttempts=3}, {@code backoffMs=50} — are externalized in {@code
 * application.yml} under {@code hist.retry} and injected via SpEL expressions in {@code @Retryable}
 * on {@code RecordAuditFactService.append()}.
 *
 * <p>Also registers {@link FactTypeCatalogProperties} for the configurable vocabulary catalog
 * (HIST-023 / SPEC-02).
 */
@Configuration
@EnableRetry
@EnableConfigurationProperties(FactTypeCatalogProperties.class)
public class RetryConfig {}
