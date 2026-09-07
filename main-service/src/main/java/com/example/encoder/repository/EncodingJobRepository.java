package com.example.encoder.repository;

import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EncodingJobRepository extends JpaRepository<EncodingJob, UUID> {

    // Şu an PROCESSING durumunda iş var mı diye kontrol eder
    boolean existsByStatus(JobStatus status);

    // PENDING durumundaki en eski (ilk oluşturulan) işi getirir
    Optional<EncodingJob> findFirstByStatusOrderByCreatedAtAsc(JobStatus status);

    // Aynı paketten (batch) doğan tüm işler
    List<EncodingJob> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    /**
     * Tüm işleri COMPLETED olan paketlerin kimlikleri.
     * Manifest üretimi (SMIL / HLS master) ancak paket tamamen bittiğinde anlamlı olduğu için
     * içinde tek bir bitmemiş iş kalan paketler bu listeye girmez.
     */
    @Query("""
            SELECT j.batchId FROM EncodingJob j
             WHERE j.batchId IS NOT NULL
             GROUP BY j.batchId
            HAVING COUNT(j) = SUM(CASE WHEN j.status = :completed THEN 1 ELSE 0 END)
            """)
    List<UUID> findFullyCompletedBatchIds(@Param("completed") JobStatus completed);
}
