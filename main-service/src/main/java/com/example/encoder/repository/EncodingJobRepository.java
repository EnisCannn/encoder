package com.example.encoder.repository;

import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EncodingJobRepository extends JpaRepository<EncodingJob, UUID> {

    // Şu an PROCESSING durumunda iş var mı diye kontrol eder
    boolean existsByStatus(JobStatus status);

    // PENDING durumundaki en eski (ilk oluşturulan) işi getirir
    Optional<EncodingJob> findFirstByStatusOrderByCreatedAtAsc(JobStatus status);
}