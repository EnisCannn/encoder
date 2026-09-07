package com.example.encoder.dto.request;

import com.example.encoder.entity.enums.SubtitleMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class CreateEncodingJobRequest {

    @NotNull(message = "Video ID boş olamaz")
    private UUID videoId;

    /**
     * Tekli mod: tek bir şablonla tek iş açılır.
     * encodeSetId verildiyse bu alan boş bırakılabilir.
     */
    private UUID presetId;

    /**
     * Paket modu: setteki her preset için ayrı bir iş açılır, hepsi aynı batchId'yi paylaşır.
     */
    private UUID encodeSetId;

    private String outputFileName; // Kullanıcı isterse kendi dosya adını verebilir

    // İsteğe bağlı medya eklemeleri (Altyazı ve Dublaj)
    private String subtitlePath;
    private String dubbingPath;

    /**
     * Altyazının nasıl uygulanacağı. Varsayılan SIDECAR: altyazı görüntüye yakılmaz,
     * ayrı bir .vtt dosyası üretilir ve oynatıcıdan açılıp kapatılabilir.
     * BURN seçilirse eski davranış (görüntüye yakma) uygulanır.
     */
    private SubtitleMode subtitleMode = SubtitleMode.SIDECAR;

    private String subtitleLanguage = "tr";
    private String subtitleLabel = "Türkçe";
}
