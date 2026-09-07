package com.example.encoder.entity.enums;

/**
 * Altyazının çıktıya nasıl uygulanacağı.
 *
 * BURN    : ffmpeg subtitles= filtresiyle görüntüye yakılır. Oynatıcıdan kapatılamaz,
 *           her kalite için ayrı ayrı gömülür.
 * SIDECAR : Ayrı bir .vtt dosyası üretilir. Oynatıcıdan açılıp kapatılabilir,
 *           tüm kaliteler aynı dosyayı paylaşır (Aşama 5'te HLS EXT-X-MEDIA olarak sunulacak).
 */
public enum SubtitleMode {
    NONE,
    BURN,
    SIDECAR
}
