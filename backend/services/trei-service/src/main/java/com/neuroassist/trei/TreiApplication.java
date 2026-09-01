package com.neuroassist.trei;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class TreiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TreiApplication.class, args);
    }
}
