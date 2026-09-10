package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut parola bos olamaz")
        String currentPassword,

        @NotBlank(message = "Yeni parola bos olamaz")
        @Size(min = 6, max = 100, message = "Yeni parola en az 6 karakter olmali")
        String newPassword
) {}
