package com.example.encoderservice.repository;

import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncodingJobRepository extends JpaRepository<EncodingJob, UUID> {

    // Şu an PROCESSING durumunda iş var mı diye kontrol eder
    boolean existsByStatus(JobStatus status);

    // PENDING durumundaki en eski (ilk oluşturulan) işi getirir
    Optional<EncodingJob> findFirstByStatusOrderByCreatedAtAsc(JobStatus status);
}