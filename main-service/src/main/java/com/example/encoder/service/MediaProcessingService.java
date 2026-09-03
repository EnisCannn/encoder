package com.example.encoder.service;

import java.util.UUID;

public interface MediaProcessingService {
    // Dışarıdan sadece bu metodun varlığı bilinir
    String encodeVideo(String fileName, UUID presetId);
}