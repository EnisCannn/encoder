package com.example.encoder.service;

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.encoder.EncoderService;
import com.example.encoder.encoder.EncodingResult;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.Video;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncodingJobService {

    private final EncodingJobRepository jobRepository;
    private final VideoRepository videoRepository;
    private final EncodingPresetRepository presetRepository;

    // Asıl FFmpeg motorumuz geri geldi
    private final EncoderService ffmpegEncoder;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    public EncodingJob createJob(CreateEncodingJobRequest request) {
        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new RuntimeException("Video bulunamadı!"));
        EncodingPreset preset = presetRepository.findById(request.getPresetId())
                .orElseThrow(() -> new RuntimeException("Preset bulunamadı!"));

        EncodingJob job = new EncodingJob();
        job.setVideo(video);
        job.setPreset(preset);
        job.setStatus(JobStatus.PENDING);
        job.setInputFileName(video.getOriginalFileName());
        job.setInputPath(video.getPath());

        String outName = request.getOutputFileName() != null ? request.getOutputFileName() : "encoded_" + video.getOriginalFileName();
        job.setOutputFileName(outName);

        return jobRepository.save(job);
    }

    // LazyInitializationException hatasını çözen notasyon eklendi
    @Transactional
    public EncodingJob startJob(UUID jobId) {
        EncodingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("İş bulunamadı!"));

        job.setStatus(JobStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        // FFmpeg işlemi tekrar buraya alındı (Zaten CronJob arka planda çalıştırıyor)
        Path outputPath = Paths.get(outputFolder, job.getOutputFileName());
        EncodingResult result = ffmpegEncoder.encode(job.getVideo(), job.getPreset(), outputPath);

        if (result.isSuccess()) {
            job.setStatus(JobStatus.COMPLETED);
            job.setOutputPath(result.getOutputPath());
        } else {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(result.getErrorMessage());
        }

        job.setCompletedAt(Instant.now());
        return jobRepository.save(job);
    }

    public EncodingJob getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("İş bulunamadı!"));
    }
}