package com.dris.v2.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendV2Application {
    public static void main(String[] args) {
        SpringApplication.run(BackendV2Application.class, args);
    }
}
