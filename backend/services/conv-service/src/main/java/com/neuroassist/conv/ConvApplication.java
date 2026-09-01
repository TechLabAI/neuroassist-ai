package com.neuroassist.conv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class ConvApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConvApplication.class, args);
    }
}
