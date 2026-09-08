package com.example.encoderservice.scheduler;

import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.JobStatus;
import com.example.encoderservice.entity.MeasurementStatus;
import com.example.encoderservice.entity.QualityMeasurement;
import com.example.encoderservice.quality.VmafOutcome;
import com.example.encoderservice.quality.VmafService;
import com.example.encoderservice.repository.EncodingJobRepository;
import com.example.encoderservice.repository.QualityMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Kalite olcumlerini isler.
 *
 * Encode kuyrugundan tamamen ayri calisir ve ondan dusuk onceliklidir: bekleyen
 * bir encode isi varsa olcum alinmaz. Boylece olcum, kullanicinin bekledigi
 * donusturme isini geciktirmez, bos zamani doldurur.
 *
 * Encode ile ayni anda calisabilmesi icin scheduler havuzu 2 thread'e ayarli
 * (application.properties -> spring.task.scheduling.pool.size).
 */
@Component
@RequiredArgsConstructor
public class QualityMeasurementScheduler {

    private final EncodingJobRepository jobRepository;
    private final QualityMeasurementRepository measurementRepository;
    private final MeasurementClaimService claimService;
    private final VmafService vmafService;

    @Value("${encoder.vmaf.enabled:true}")
    private boolean vmafEnabled;

    @Scheduled(fixedDelayString = "${encoder.vmaf.check-interval-ms:5000}")
    public void checkForPendingMeasurements() {
        if (!vmafEnabled) {
            return;
        }

        // Encode her zaman oncelikli: bekleyen is varken olcume baslamiyoruz.
        if (jobRepository.existsByStatus(JobStatus.PENDING)) {
            return;
        }

        Optional<QualityMeasurement> claimedOpt = claimService.claimNextPendingMeasurement();
        if (claimedOpt.isEmpty()) {
            return;
        }

        QualityMeasurement measurement = claimedOpt.get();
        Optional<EncodingJob> jobOpt = jobRepository.findById(measurement.getEncodingJobId());

        if (jobOpt.isEmpty()) {
            finish(measurement, MeasurementStatus.FAILED, "Olculecek is bulunamadi.");
            return;
        }

        EncodingJob job = jobOpt.get();
        System.out.println("Kalite olcumu basliyor. Job ID: " + job.getId());

        VmafOutcome outcome = vmafService.measure(job);

        switch (outcome.getKind()) {
            case MEASURED -> {
                measurement.setVmafScore(outcome.getMean());
                measurement.setVmafMin(outcome.getMin());
                measurement.setVmafHarmonicMean(outcome.getHarmonicMean());
                measurement.setSampledSeconds(outcome.getSampledSeconds());
                measurement.setOutputFrameRate(outcome.getOutputFrameRate());
                finish(measurement, MeasurementStatus.COMPLETED, null);

                // Arayuz listeyi cekerken join yapmasin diye ise de kopyaliyoruz
                job.setVmafScore(outcome.getMean());
                jobRepository.save(job);

                System.out.printf("Kalite olcumu bitti. Job ID: %s, VMAF: %.2f%n",
                        job.getId(), outcome.getMean());
            }
            case SKIPPED -> {
                finish(measurement, MeasurementStatus.SKIPPED, outcome.getMessage());
                System.out.println("Kalite olcumu atlandi (" + outcome.getMessage() + ")");
            }
            case FAILED -> {
                finish(measurement, MeasurementStatus.FAILED, outcome.getMessage());
                System.out.println("Kalite olcumu basarisiz: " + outcome.getMessage());
            }
        }
    }

    private void finish(QualityMeasurement measurement, MeasurementStatus status, String message) {
        measurement.setStatus(status);
        measurement.setErrorMessage(message);
        measurement.setCompletedAt(Instant.now());
        measurementRepository.save(measurement);
    }
}
