package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.FactTypeCatalogUseCase;
import com.neuroassist.hist.infrastructure.config.FactTypeCatalogProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — returns the active controlled vocabulary (HIST-023 / SPEC-02).
 *
 * <p>The vocabulary list is driven by {@link FactTypeCatalogProperties} bound from {@code
 * hist.fact-types.allowed} in {@code application.yml}. Adding a new type to the YAML does NOT
 * require recompilation — the next JVM restart exposes it immediately (SPEC-02).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactTypeCatalogService implements FactTypeCatalogUseCase {

    private static final String VOCABULARY_VERSION = "v1";

    private final FactTypeCatalogProperties catalogProperties;

    @Override
    @Transactional(readOnly = true)
    public List<FactTypeEntry> listActive() {
        List<FactTypeEntry> entries =
                catalogProperties.getAllowed().stream()
                        .map(code -> new FactTypeEntry(code, VOCABULARY_VERSION))
                        .toList();
        log.debug("Returning {} fact-type entries from configured vocabulary", entries.size());
        return entries;
    }
}
