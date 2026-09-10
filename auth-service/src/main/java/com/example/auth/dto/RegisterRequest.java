package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Kullanici adi bos olamaz")
        @Size(min = 3, max = 50, message = "Kullanici adi 3-50 karakter olmali")
        String username,

        @NotBlank(message = "Parola bos olamaz")
        @Size(min = 6, max = 100, message = "Parola en az 6 karakter olmali")
        String password
) {}
