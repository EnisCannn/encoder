package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.QualityCurvePoint;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.MeasurementStatus;
import com.example.encoder.entity.QualityMeasurement;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.repository.QualityMeasurementRepository;
import com.example.encoder.service.QualityMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ölçüm kayıtlarını oluşturur. Ölçümün kendisini worker'lar (encoder-service)
 * yapar; burada sadece kuyruğa satır yazılıyor.
 */
@Service
@RequiredArgsConstructor
public class QualityMeasurementServiceImpl implements QualityMeasurementService {

    private final QualityMeasurementRepository measurementRepository;
    private final EncodingJobRepository jobRepository;

    @Override
    @Transactional
    public List<QualityMeasurement> requestMeasurements(List<UUID> jobIds) {
        List<QualityMeasurement> queued = new ArrayList<>();

        for (UUID jobId : jobIds) {
            EncodingJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                continue;
            }

            // Tamamlanmamış işin çıktısı yok; ölçüm kuyruğa alınmaz.
            if (job.getStatus() != JobStatus.COMPLETED) {
                continue;
            }

            QualityMeasurement existing = measurementRepository.findByEncodingJobId(jobId).orElse(null);

            // Halihazırda sırada ya da ölçülüyorsa tekrar kuyruğa atmıyoruz.
            // Kontrol yalnızca kayıtlı satır için geçerli: yeni nesne de PENDING
            // ile başladığı için ayrım yapılmazsa hiç kaydedilmeden atlanır.
            if (existing != null
                    && (existing.getStatus() == MeasurementStatus.PENDING
                        || existing.getStatus() == MeasurementStatus.PROCESSING)) {
                queued.add(existing);
                continue;
            }

            QualityMeasurement measurement = existing != null ? existing : new QualityMeasurement();
            measurement.setEncodingJobId(jobId);
            measurement.setStatus(MeasurementStatus.PENDING);
            measurement.setErrorMessage(null);
            measurement.setStartedAt(null);
            measurement.setCompletedAt(null);
            measurement.setCreatedAt(Instant.now());
            queued.add(measurementRepository.save(measurement));
        }

        return queued;
    }

    @Override
    @Transactional
    public List<QualityMeasurement> requestForBatch(UUID batchId) {
        List<UUID> jobIds = jobRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .map(EncodingJob::getId)
                .toList();
        return requestMeasurements(jobIds);
    }

    /**
     * Eğri noktalarını üretir. Ölçüm kaydında yalnızca is kimliği var; bitrate ve
     * çözünürlük şablondan, video adı isten geliyor. Isler tek sorguda cekilip
     * bellekte eşleştiriliyor, ölçüm başına ayrı sorgu atılmıyor.
     */
    @Override
    public List<QualityCurvePoint> getCurvePoints() {
        List<QualityMeasurement> completed =
                measurementRepository.findByStatus(MeasurementStatus.COMPLETED);
        if (completed.isEmpty()) {
            return List.of();
        }

        List<UUID> jobIds = completed.stream().map(QualityMeasurement::getEncodingJobId).toList();
        Map<UUID, EncodingJob> jobsById = jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(EncodingJob::getId, Function.identity()));

        List<QualityCurvePoint> points = new ArrayList<>();
        for (QualityMeasurement m : completed) {
            EncodingJob job = jobsById.get(m.getEncodingJobId());
            if (job == null || m.getVmafScore() == null) {
                continue;
            }
            EncodingPreset preset = job.getPreset();
            if (preset == null || preset.getVideoBitrate() == null || preset.getHeight() == null) {
                continue;
            }

            points.add(new QualityCurvePoint(
                    job.getId(),
                    job.getVideo() != null ? job.getVideo().getId() : null,
                    job.getInputFileName(),
                    preset.getName(),
                    preset.getWidth(),
                    preset.getHeight(),
                    preset.getVideoBitrate(),
                    preset.getFrameRate(),
                    m.getOutputFrameRate(),
                    m.getVmafScore(),
                    m.getVmafMin(),
                    m.getVmafHarmonicMean(),
                    m.getSampledSeconds(),
                    m.getCompletedAt()));
        }
        return points;
    }

    @Override
    public List<QualityMeasurement> getMeasurements(List<UUID> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return List.of();
        }
        return measurementRepository.findByEncodingJobIdIn(jobIds);
    }
}
