package com.salescms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SalesCmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesCmsApplication.class, args);
    }
}
