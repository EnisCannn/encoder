package com.example.encoder.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EncodeSetResponse(
        UUID id,
        String name,
        String description,
        List<PresetResponse> presets,
        Boolean isActive,
        Instant createdAt
) {}
