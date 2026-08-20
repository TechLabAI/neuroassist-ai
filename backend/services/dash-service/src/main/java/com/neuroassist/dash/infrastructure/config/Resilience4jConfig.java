package com.neuroassist.dash.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Circuit breakers Resilience4j por upstream — configuracao declarativa via application.yml.
 * <p>
 * Degradacao matrix:
 * <ul>
 *   <li>acs-service  — hard-503 (sem identidade nada funciona)</li>
 *   <li>pac-service  — hard-503 para overview/calibracao</li>
 *   <li>evnt-service — hard-503 para writes; soft-degrade para reads</li>
 *   <li>hist-service — hard-503 para historico; soft-degrade para summary</li>
 *   <li>edge-service — soft-degrade em todos os casos</li>
 * </ul>
 */
@Configuration
public class Resilience4jConfig {
    // Instancias declaradas em application.yml (resilience4j.circuitbreaker / timelimiter).
    // Nenhum bean programatico necessario — spring-cloud-starter-circuitbreaker-resilience4j
    // configura automaticamente a partir do yml.
}
