package com.example.encoderservice.repository;

import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncodingJobRepository extends JpaRepository<EncodingJob, UUID> {

    // Şu an PROCESSING durumunda iş var mı diye kontrol eder
    boolean existsByStatus(JobStatus status);

    // PENDING durumundaki en eski (ilk oluşturulan) işi getirir
    Optional<EncodingJob> findFirstByStatusOrderByCreatedAtAsc(JobStatus status);

    /**
     * Sıradaki PENDING işin satırını kilitleyip ID'sini döner.
     * FOR UPDATE SKIP LOCKED sayesinde başka bir worker'ın halihazırda kilitlediği
     * satır atlanır; böylece iki replika aynı işi kapamaz.
     * Kilit, çağıran transaction commit olana kadar tutulur.
     */
    @Query(value = """
            SELECT id FROM encoding_jobs
             WHERE status = 'PENDING'
             ORDER BY created_at ASC
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> lockNextPendingJobId();

    // Aynı paketten (batch) doğan tüm işler - Aşama 4/5'te manifest üretimi için
    List<EncodingJob> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
}
