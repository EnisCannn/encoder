package com.example.encoder.scheduler;

import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.repository.QualityMeasurementRepository;
import com.example.encoder.service.QualityMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Kalibrasyon isleri tamamlaninca olcumlerini otomatik kuyruga alir.
 *
 * Tarama baslatildiginda isler daha PENDING oldugu icin olcum kaydi acilamaz
 * (olcum tamamlanmis bir cikti ister). Kullaniciyi her is bitince "Kaliteyi Olc"
 * dugmesine basmaya zorlamamak icin bu adim burada otomatiklestiriliyor.
 *
 * Sadece kalibrasyon sablonuyla acilmis isleri kapsar; normal isler eskisi gibi
 * elle olculmeye devam eder.
 */
@Component
@RequiredArgsConstructor
public class CalibrationMeasurementScheduler {

    private final EncodingJobRepository jobRepository;
    private final QualityMeasurementRepository measurementRepository;
    private final QualityMeasurementService measurementService;

    @Scheduled(fixedDelayString = "${encoder.calibration.check-interval-ms:10000}")
    public void queueFinishedCalibrationJobs() {
        List<EncodingJob> candidates = jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .filter(j -> j.getPreset() != null
                        && Boolean.TRUE.equals(j.getPreset().getCalibration()))
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        List<UUID> jobIds = candidates.stream().map(EncodingJob::getId).toList();
        Set<UUID> alreadyQueued = measurementRepository.findByEncodingJobIdIn(jobIds).stream()
                .map(m -> m.getEncodingJobId())
                .collect(Collectors.toSet());

        List<UUID> missing = jobIds.stream().filter(id -> !alreadyQueued.contains(id)).toList();
        if (missing.isEmpty()) {
            return;
        }

        measurementService.requestMeasurements(missing);
        System.out.println("Kalibrasyon olcumu kuyruga alindi: " + missing.size() + " is");
    }
}
