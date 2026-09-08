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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
            EncodingPreset preset = buildCalibrationPreset(request, bitrate);
            presetRepository.save(preset);

            CreateEncodingJobRequest jobRequest = new CreateEncodingJobRequest();
            jobRequest.setVideoId(video.getId());
            jobRequest.setPresetId(preset.getId());
            jobRequest.setSubtitleMode(SubtitleMode.NONE);

            jobs.addAll(encodingJobService.createJobs(jobRequest));
        }
        return jobs;
    }

    private EncodingPreset buildCalibrationPreset(CalibrationSweepRequest request, Integer bitrate) {
        EncodingPreset preset = new EncodingPreset();
        preset.setName("Kalibrasyon " + request.getHeight() + "p " + bitrate + "k"
                + (request.getFrameRate() != null ? " @" + request.getFrameRate().stripTrailingZeros().toPlainString() : ""));
        preset.setDescription("Kalite kalibrasyon taraması");
        preset.setCalibration(true);
        preset.setFormat(Format.MP4);
        preset.setVideoCodec(VideoCodec.H264);
        preset.setAudioCodec(AudioCodec.AAC);
        preset.setWidth(request.getWidth());
        preset.setHeight(request.getHeight());
        preset.setVideoBitrate(bitrate);
        preset.setAudioBitrate(request.getAudioBitrate() != null ? request.getAudioBitrate() : 128);
        // frameRate null birakilirsa encoder -r eklemiyor, kaynagin hizi korunuyor
        preset.setFrameRate(request.getFrameRate());
        return preset;
    }
}
