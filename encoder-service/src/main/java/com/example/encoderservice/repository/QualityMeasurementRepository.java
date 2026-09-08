package com.example.encoderservice.repository;

import com.example.encoderservice.entity.QualityMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualityMeasurementRepository extends JpaRepository<QualityMeasurement, UUID> {

    /**
     * Siradaki PENDING olcumun satirini kilitleyip ID'sini doner.
     * Encode kuyrugundaki ile ayni desen: FOR UPDATE SKIP LOCKED sayesinde iki
     * worker ayni olcumu kapamaz.
     */
    @Query(value = """
            SELECT id FROM quality_measurements
             WHERE status = 'PENDING'
             ORDER BY created_at ASC
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> lockNextPendingMeasurementId();

    Optional<QualityMeasurement> findByEncodingJobId(UUID encodingJobId);
}
