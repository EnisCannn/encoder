package com.example.encoderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class EncoderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EncoderServiceApplication.class, args);
    }

}
