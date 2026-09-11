package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Kullanici adi bos olamaz") String username,
        @NotBlank(message = "Parola bos olamaz") String password
) {}
