package com.example.encoder.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class CreateEncodingJobRequest {

    @NotNull(message = "Video ID boş olamaz")
    private UUID videoId;

    @NotNull(message = "Preset ID boş olamaz")
    private UUID presetId;

    private String outputFileName; // Kullanıcı isterse kendi dosya adını verebilir

    // İsteğe bağlı medya eklemeleri (Altyazı ve Dublaj)
    private String subtitlePath;
    private String dubbingPath;
}