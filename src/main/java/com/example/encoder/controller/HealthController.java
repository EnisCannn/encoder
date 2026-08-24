package com.example.encoder.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // Bu sınıfın bir web API'si olduğunu Spring'e bildirir
@RequestMapping("/api") // Bu sınıftaki tüm adreslerin "/api" ile başlamasını sağlar
public class HealthController {

    @GetMapping("/health") // Tarayıcıdan "/api/health" adresine girildiğinde bu metod çalışır
    public String healthCheck() {
        return "Uygulama ayakta, veritabanına bağlı ve çalışıyor!";
    }
}