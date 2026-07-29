package com.neuroassist.pac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class PacApplication {

    public static void main(String[] args) {
        SpringApplication.run(PacApplication.class, args);
    }
}
