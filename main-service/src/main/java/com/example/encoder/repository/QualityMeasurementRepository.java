package com.example.encoder.repository;

import com.example.encoder.entity.MeasurementStatus;
import com.example.encoder.entity.QualityMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualityMeasurementRepository extends JpaRepository<QualityMeasurement, UUID> {

    Optional<QualityMeasurement> findByEncodingJobId(UUID encodingJobId);

    List<QualityMeasurement> findByEncodingJobIdIn(Collection<UUID> encodingJobIds);

    List<QualityMeasurement> findByStatus(MeasurementStatus status);
}
