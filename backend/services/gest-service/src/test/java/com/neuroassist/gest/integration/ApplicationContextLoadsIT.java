package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class ApplicationContextLoadsIT extends BaseIntegrationTest {

    private ApplicationContext context;

    @Autowired
    void setContext(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
