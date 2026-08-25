package com.example.encoder.dto;

import com.example.encoder.entity.enums.AudioCodec;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePresetRequest(
        @NotBlank(message = "Preset adı kesinlikle boş bırakılamaz!")
        String name,

        String description,
        Format format,
        VideoCodec videoCodec,
        AudioCodec audioCodec,

        @Positive(message = "Genişlik eksi veya sıfır olamaz!")
        Integer width,

        @Positive(message = "Yükseklik eksi veya sıfır olamaz!")
        Integer height,

        @Positive(message = "Video bitrate (kalite) sıfırdan büyük olmalı!")
        Integer videoBitrate,

        @Positive(message = "Audio bitrate sıfırdan büyük olmalı!")
        Integer audioBitrate,

        @Positive(message = "Frame rate (FPS) sıfırdan büyük olmalı!")
        BigDecimal frameRate
) {}