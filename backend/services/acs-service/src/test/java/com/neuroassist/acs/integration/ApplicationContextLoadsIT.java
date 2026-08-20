package com.neuroassist.acs.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class ApplicationContextLoadsIT extends BaseIntegrationTest {

    private final ApplicationContext context;

    ApplicationContextLoadsIT(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
