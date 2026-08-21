package com.neuroassist.dash.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

class ApplicationContextLoadsIT extends BaseIntegrationTest implements ApplicationContextAware {

    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.context = ctx;
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
