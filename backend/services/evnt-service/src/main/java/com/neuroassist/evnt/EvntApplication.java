package com.neuroassist.evnt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class EvntApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvntApplication.class, args);
    }
}
