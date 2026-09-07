package com.example.encoderservice.scheduler;

import com.example.encoderservice.encoder.EncoderService;
import com.example.encoderservice.encoder.EncodingResult;
import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.JobStatus;
import com.example.encoderservice.repository.EncodingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EncodingJobScheduler {

    private final EncodingJobRepository jobRepository;
    private final JobClaimService jobClaimService;
    private final EncoderService encoderService;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    // Her 2 saniyede bir otomatik çalışır
    @Scheduled(fixedDelay = 2000)
    public void checkForPendingJobs() {
        // İşi kapma ayrı bir transaction içinde ve satır kilidiyle yapılıyor;
        // iki replika aynı işi almaz.
        Optional<EncodingJob> claimedOpt = jobClaimService.claimNextPendingJob();
        if (claimedOpt.isEmpty()) {
            return;
        }

        EncodingJob job = claimedOpt.get();
        System.out.println("İş kapıldı, başlanıyor... Job ID: " + job.getId()
                + (job.getBatchId() != null ? " (Paket: " + job.getBatchId() + ")" : ""));

        // Paket işlerinde çıktı adı "<batchId>/<preset>.mp4" şeklinde alt klasör içerir
        Path outputPath = Paths.get(outputFolder, job.getOutputFileName());

        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Çıktı klasörü oluşturulamadı: " + e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            System.out.println("Çıktı klasörü oluşturulamadı: " + e.getMessage());
            return;
        }

        EncodingResult result = encoderService.encode(job, outputPath);

        if (result.isSuccess()) {
            job.setStatus(JobStatus.COMPLETED);
            job.setOutputPath(outputPath.toString());
            job.setCompletedAt(Instant.now());
            job.setProgress(100);
            System.out.println("İşlem başarıyla tamamlandı. Job ID: " + job.getId());
        } else {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(result.getErrorMessage());
            job.setCompletedAt(Instant.now());
            System.out.println("İşlem başarısız oldu: " + result.getErrorMessage());
        }
        jobRepository.save(job);
    }
}
