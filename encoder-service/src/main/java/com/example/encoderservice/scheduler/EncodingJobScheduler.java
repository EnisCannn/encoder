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
import java.time.Instant;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EncodingJobScheduler {

    private final EncodingJobRepository jobRepository;
    private final EncoderService encoderService;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    // Her 2 saniyede bir otomatik çalışır
    @Scheduled(fixedDelay = 2000)
    public void checkForPendingJobs() {
        System.out.println("--- İşçi Veritabanını Kontrol Ediyor: PENDING iş aranıyor... ---");

        Optional<EncodingJob> pendingJobOpt = jobRepository.findFirstByStatusOrderByCreatedAtAsc(JobStatus.PENDING);

        if (pendingJobOpt.isPresent()) {
            EncodingJob job = pendingJobOpt.get();
            System.out.println("Yeni iş bulundu! Başlanıyor... Job ID: " + job.getId());

            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            Path outputPath = Paths.get(outputFolder, job.getOutputFileName());
            EncodingResult result = encoderService.encode(job, outputPath);

            if (result.isSuccess()) {
                job.setStatus(JobStatus.COMPLETED);
                job.setOutputPath(outputPath.toString());
                job.setCompletedAt(Instant.now());
                job.setProgress(100);
                System.out.println("İşlem başarıyla tamamlandı.");
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(Instant.now());
                System.out.println("İşlem başarısız oldu: " + result.getErrorMessage());
            }
            jobRepository.save(job);
        }
    }
}