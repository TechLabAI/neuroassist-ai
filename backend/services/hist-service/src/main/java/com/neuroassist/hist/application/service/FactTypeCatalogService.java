package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.FactTypeCatalogUseCase;
import com.neuroassist.hist.domain.model.FactType;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — returns the active controlled vocabulary. v1 seed is derived from the
 * {@link FactType} enum; no DB access required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactTypeCatalogService implements FactTypeCatalogUseCase {

    private static final String VOCABULARY_VERSION = "v1";

    @Override
    @Transactional(readOnly = true)
    public List<FactTypeEntry> listActive() {
        return Arrays.stream(FactType.values())
                .map(ft -> new FactTypeEntry(ft.code(), VOCABULARY_VERSION))
                .toList();
    }
}
