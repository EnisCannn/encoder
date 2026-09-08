package com.example.encoder.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kalite analizi grafiğinin tek bir noktası: bir encode çıktısının bitrate'i ve
 * aldığı VMAF skoru.
 *
 * Eğriler ayrı bir tabloda saklanmıyor; aynı video + aynı çözünürlükteki
 * noktalar arayüzde gruplanınca eğri kendiliğinden oluşuyor.
 */
public record QualityCurvePoint(
        UUID jobId,
        UUID videoId,
        String videoName,
        String presetName,
        Integer width,
        Integer height,
        Integer videoBitrate,
        BigDecimal frameRate,
        Double outputFrameRate,
        Double vmafScore,
        Double vmafMin,
        Double vmafHarmonicMean,
        Integer sampledSeconds,
        Instant measuredAt
) {
}
