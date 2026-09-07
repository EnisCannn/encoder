package com.example.encoderservice.scheduler;

import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.JobStatus;
import com.example.encoderservice.repository.EncodingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Kuyruktan iş kapma işini tek bir transaction içinde yapar.
 *
 * Eskiden scheduler önce PENDING işi okuyup sonra PROCESSING olarak kaydediyordu;
 * iki okuma arasında ikinci bir worker aynı işi görebiliyordu. Artık satır
 * FOR UPDATE SKIP LOCKED ile kilitlenip aynı transaction içinde PROCESSING'e
 * çekiliyor, yani bir işi yalnızca tek bir worker alabiliyor.
 */
@Service
@RequiredArgsConstructor
public class JobClaimService {

    private final EncodingJobRepository jobRepository;

    @Transactional
    public Optional<EncodingJob> claimNextPendingJob() {
        return jobRepository.lockNextPendingJobId()
                .flatMap(jobRepository::findById)
                .map(job -> {
                    job.setStatus(JobStatus.PROCESSING);
                    job.setStartedAt(Instant.now());
                    job.setProgress(0);
                    return jobRepository.save(job);
                });
    }
}
