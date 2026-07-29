package com.neuroassist.gest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class GestApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestApplication.class, args);
    }
}
