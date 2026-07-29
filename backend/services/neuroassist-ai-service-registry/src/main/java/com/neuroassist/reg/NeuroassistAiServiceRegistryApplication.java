package com.neuroassist.reg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class NeuroassistAiServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeuroassistAiServiceRegistryApplication.class, args);
    }
}
