package com.neuroassist.pac.integration;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
class ApplicationContextLoadsIT extends BaseIntegrationTest {

    private final ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
