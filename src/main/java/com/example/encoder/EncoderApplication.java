package com.example.encoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // YENİ

@SpringBootApplication
@EnableScheduling // Spring'e "Zamanlanmış görevleri çalıştırabilirsin" diyoruz
public class EncoderApplication {
    public static void main(String[] args) {
        SpringApplication.run(EncoderApplication.class, args);
    }
}