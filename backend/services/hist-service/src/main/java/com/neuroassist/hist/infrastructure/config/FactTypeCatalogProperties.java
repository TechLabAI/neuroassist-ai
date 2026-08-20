package com.neuroassist.hist.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the fact-type controlled vocabulary (HIST-023 / SPEC-02).
 *
 * <p>Populated from {@code hist.fact-types.allowed} in {@code application.yml}. Changing the list
 * does NOT require recompilation — any type added to the YAML is immediately accepted during
 * ingestion.
 *
 * <p>Registered via {@code @EnableConfigurationProperties} in {@link RetryConfig}.
 */
@Data
@ConfigurationProperties(prefix = "hist.fact-types")
public class FactTypeCatalogProperties {

    /**
     * Ordered list of allowed fact-type codes for the active vocabulary version. v1 seed ships 10
     * entries; the list is extended exclusively via {@code application.yml}.
     */
    private List<String> allowed = new ArrayList<>();
}
