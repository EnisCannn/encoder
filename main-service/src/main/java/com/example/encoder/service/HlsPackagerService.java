package com.example.encoder.service;

import java.nio.file.Path;
import java.util.UUID;

public interface HlsPackagerService {

    /**
     * Paketin her mp4 çıktısından bir HLS varyantı üretir ve master playlist yazar.
     * Yeniden kodlama yapılmaz, yalnızca segmentlere bölünür (-c copy).
     *
     * @return yazılan master.m3u8 dosyasının yolu
     */
    Path packageBatch(UUID batchId);
}
