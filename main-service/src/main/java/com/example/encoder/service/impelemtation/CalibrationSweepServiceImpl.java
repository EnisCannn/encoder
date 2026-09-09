package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.CalibrationSweepRequest;
import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.Video;
import com.example.encoder.entity.enums.AudioCodec;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.SubtitleMode;
import com.example.encoder.entity.enums.VideoCodec;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.CalibrationSweepService;
import com.example.encoder.service.EncodingJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Kalite kalibrasyon taramasi: ayni videoyu ayni cozunurlukte farkli
 * bitrate'lerle encode edip her birinin VMAF skorunu olcturur. Cikan egri,
 * "bu cozunurlukte bitrate artirmanin nerede anlamsizlastigini" gosterir.
 *
 * Sablonlar gecici olarak isaretlenir, Sablonlar sayfasinda gorunmezler.
 */
@Service
@RequiredArgsConstructor
public class CalibrationSweepServiceImpl implements CalibrationSweepService {

    private final VideoRepository videoRepository;
    private final EncodingPresetRepository presetRepository;
    private final EncodingJobService encodingJobService;

    @Override
    @Transactional
    public List<EncodingJob> startSweep(CalibrationSweepRequest request) {
        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new RuntimeException("Video bulunamadı!"));

        // Ayni bitrate iki kez girilirse tek is acilir; tekrar noktalar egriyi bozar
        List<Integer> bitrates = new ArrayList<>(new LinkedHashSet<>(request.getBitrates()));
        bitrates.removeIf(b -> b == null || b <= 0);
        if (bitrates.isEmpty()) {
            throw new RuntimeException("Geçerli bir bitrate girilmedi!");
        }
        bitrates.sort(Integer::compareTo);

        List<EncodingJob> jobs = new ArrayList<>();
        for (Integer bitrate : bitrates) {
            EncodingPreset preset = findOrCreateCalibrationPreset(request, bitrate);

            CreateEncodingJobRequest jobRequest = new CreateEncodingJobRequest();
            jobRequest.setVideoId(video.getId());
            jobRequest.setPresetId(preset.getId());
            jobRequest.setSubtitleMode(SubtitleMode.NONE);

            jobs.addAll(encodingJobService.createJobs(jobRequest));
        }
        return jobs;
    }

    /**
     * Kalibrasyon sablonunu bulur ya da olusturur.
     *
     * Sablon adi benzersiz (unique constraint) ve ad yalnizca cozunurluk +
     * bitrate + fps'ten uretiliyor. Bu yuzden ayni taramayi ikinci kez
     * calistirmak "duplicate key" hatasi veriyordu: eski taramanin sablonu
     * hala duruyorsa ayni ad tekrar uretiliyordu.
     *
     * Cozum sablonu yeniden kullanmak. Kalibrasyon sablonu duzenlenebilir bir
     * kullanici kaydi degil, sadece bir parametre demeti; ayni parametrelerin
     * iki kopyasini tutmanin anlami yok. Ad baska bir sablona aitse (ornegin
     * kullanicinin kendi olusturdugu bir sablon ya da ayni ada denk gelen
     * farkli genislik) ada bir ek konup yeni sablon aciliyor.
     */
    private EncodingPreset findOrCreateCalibrationPreset(CalibrationSweepRequest request, Integer bitrate) {
        String name = calibrationName(request, bitrate);

        Optional<EncodingPreset> existing = presetRepository.findByName(name);
        if (existing.isPresent()) {
            if (matchesRequest(existing.get(), request, bitrate)) {
                return existing.get();
            }
            name = firstFreeName(name);
        }

        EncodingPreset preset = buildCalibrationPreset(request, bitrate);
        preset.setName(name);
        return presetRepository.save(preset);
    }

    private String calibrationName(CalibrationSweepRequest request, Integer bitrate) {
        return "Kalibrasyon " + request.getHeight() + "p " + bitrate + "k"
                + (request.getFrameRate() != null
                        ? " @" + request.getFrameRate().stripTrailingZeros().toPlainString()
                        : "");
    }

    /** Ayni adi tasiyan sablon gercekten ayni isi yapiyor mu? */
    private boolean matchesRequest(EncodingPreset preset, CalibrationSweepRequest request, Integer bitrate) {
        return Boolean.TRUE.equals(preset.getCalibration())
                && Objects.equals(preset.getWidth(), request.getWidth())
                && Objects.equals(preset.getHeight(), request.getHeight())
                && Objects.equals(preset.getVideoBitrate(), bitrate)
                && sameFrameRate(preset.getFrameRate(), request.getFrameRate())
                && Objects.equals(preset.getAudioBitrate(), audioBitrateOf(request));
    }

    /** 25 ile 25.00 ayni kare hizi; BigDecimal.equals olcegi de karsilastirdigi icin compareTo. */
    private boolean sameFrameRate(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    private Integer audioBitrateOf(CalibrationSweepRequest request) {
        return request.getAudioBitrate() != null ? request.getAudioBitrate() : 128;
    }

    private String firstFreeName(String base) {
        for (int i = 2; i <= 100; i++) {
            String candidate = base + " #" + i;
            if (!presetRepository.existsByName(candidate)) {
                return candidate;
            }
        }
        throw new RuntimeException("Kalibrasyon şablonu için boş ad bulunamadı: " + base);
    }

    private EncodingPreset buildCalibrationPreset(CalibrationSweepRequest request, Integer bitrate) {
        EncodingPreset preset = new EncodingPreset();
        preset.setDescription("Kalite kalibrasyon taraması");
        preset.setCalibration(true);
        preset.setFormat(Format.MP4);
        preset.setVideoCodec(VideoCodec.H264);
        preset.setAudioCodec(AudioCodec.AAC);
        preset.setWidth(request.getWidth());
        preset.setHeight(request.getHeight());
        preset.setVideoBitrate(bitrate);
        preset.setAudioBitrate(audioBitrateOf(request));
        // frameRate null birakilirsa encoder -r eklemiyor, kaynagin hizi korunuyor
        preset.setFrameRate(request.getFrameRate());
        return preset;
    }
}
