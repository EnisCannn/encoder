package com.example.encoderservice.entity.enums;

/**
 * Altyazının çıktıya nasıl uygulanacağı. main-service'teki enum ile birebir aynı olmalı.
 *
 * BURN    : ffmpeg subtitles= filtresiyle görüntüye yakılır, kapatılamaz.
 * SIDECAR : Ayrı .vtt dosyası main-service tarafından üretilir, worker yakma yapmaz.
 */
public enum SubtitleMode {
    NONE,
    BURN,
    SIDECAR
}
