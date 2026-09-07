package com.example.encoder.service;

import java.nio.file.Path;

public interface SubtitleService {

    /**
     * Yüklenen altyazıyı WebVTT'ye çevirip hedef klasöre yazar.
     * Kaynak zaten .vtt ise kopyalanır, .srt ise ffmpeg ile dönüştürülür.
     *
     * @return üretilen .vtt dosyasının tam yolu
     */
    Path convertToVtt(String sourcePath, Path targetDir, String baseName);
}
