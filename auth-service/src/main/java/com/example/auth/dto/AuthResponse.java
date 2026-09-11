package com.example.auth.dto;

/** Girisin sonucu: istemci token'i saklar, digerleri arayuzde gosterilir. */
public record AuthResponse(
        String token,
        String username,
        String role,
        long expiresInSeconds
) {}
