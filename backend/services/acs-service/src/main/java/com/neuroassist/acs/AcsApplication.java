package com.neuroassist.acs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class AcsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcsApplication.class, args);
    }
}
