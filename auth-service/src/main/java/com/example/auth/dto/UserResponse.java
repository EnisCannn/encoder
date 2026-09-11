package com.example.auth.dto;

import java.time.Instant;

public record UserResponse(
        String id,
        String username,
        String role,
        boolean enabled,
        Instant createdAt
) {}
