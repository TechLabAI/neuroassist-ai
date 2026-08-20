package com.neuroassist.dash.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class ApplicationContextLoadsIT extends BaseIntegrationTest {

    private final ApplicationContext context;

    public ApplicationContextLoadsIT(
            org.springframework.boot.test.web.client.TestRestTemplate restTemplate,
            com.neuroassist.dash.infrastructure.sse.PanelSessionRegistry panelSessionRegistry,
            ApplicationContext context) {
        super(restTemplate, panelSessionRegistry);
        this.context = context;
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
