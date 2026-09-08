package com.example.encoder.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Bir kalite kalibrasyon taramasi: ayni video, ayni cozunurluk, farkli bitrate'ler.
 * Her bitrate icin gecici bir sablon ve bir encode isi acilir; isler bitince
 * olcumleri otomatik kuyruga girer.
 */
@Getter
@Setter
public class CalibrationSweepRequest {

    @NotNull(message = "Video secilmeli")
    private UUID videoId;

    @NotNull(message = "Genislik gerekli")
    private Integer width;

    @NotNull(message = "Yukseklik gerekli")
    private Integer height;

    /** Bos birakilirsa kaynagin kare hizi korunur - onerilen davranis. */
    private BigDecimal frameRate;

    @NotEmpty(message = "En az bir bitrate girilmeli")
    private List<Integer> bitrates;

    private Integer audioBitrate = 128;
}
