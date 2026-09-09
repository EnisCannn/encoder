package com.example.encoder.dto;

import com.example.encoder.entity.enums.AudioCodec;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;
import java.math.BigDecimal;
import java.util.UUID;

public record PresetResponse(
        UUID id,
        String name,
        String description,
        Format format,
        VideoCodec videoCodec,
        AudioCodec audioCodec,
        Integer width,
        Integer height,
        Integer videoBitrate,
        Integer audioBitrate,
        BigDecimal frameRate,
        Boolean isActive,
        /** Kalibrasyon taramasinin urettigi gecici sablon mu? Arayuz ayirt etsin diye. */
        Boolean calibration
) {}