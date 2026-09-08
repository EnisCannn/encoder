package com.example.encoderservice.scheduler;

import com.example.encoderservice.entity.MeasurementStatus;
import com.example.encoderservice.entity.QualityMeasurement;
import com.example.encoderservice.repository.QualityMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Olcum kuyrugundan is kapar. Encode kuyrugundaki JobClaimService ile ayni desen:
 * satir FOR UPDATE SKIP LOCKED ile kilitlenip ayni transaction icinde PROCESSING'e
 * cekiliyor, boylece iki worker ayni olcumu almiyor.
 */
@Service
@RequiredArgsConstructor
public class MeasurementClaimService {

    private final QualityMeasurementRepository measurementRepository;

    @Transactional
    public Optional<QualityMeasurement> claimNextPendingMeasurement() {
        return measurementRepository.lockNextPendingMeasurementId()
                .flatMap(measurementRepository::findById)
                .map(measurement -> {
                    measurement.setStatus(MeasurementStatus.PROCESSING);
                    measurement.setStartedAt(Instant.now());
                    measurement.setErrorMessage(null);
                    return measurementRepository.save(measurement);
                });
    }
}
