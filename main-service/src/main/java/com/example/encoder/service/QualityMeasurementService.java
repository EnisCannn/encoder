package com.example.encoder.service;

import com.example.encoder.dto.QualityCurvePoint;
import com.example.encoder.entity.QualityMeasurement;

import java.util.List;
import java.util.UUID;

public interface QualityMeasurementService {

    /**
     * Verilen işler için kalite ölçümünü kuyruğa alır. Daha önce ölçülmüş bir iş
     * yeniden kuyruğa girer (aynı satır PENDING'e döner), böylece şablon değişince
     * tekrar ölçebilirsin.
     */
    List<QualityMeasurement> requestMeasurements(List<UUID> jobIds);

    /** Bir paketteki tüm işleri kuyruğa alır. */
    List<QualityMeasurement> requestForBatch(UUID batchId);

    List<QualityMeasurement> getMeasurements(List<UUID> jobIds);

    /** Kalite analizi grafiği için tamamlanmış tüm ölçümler, şablon ve video bilgisiyle. */
    List<QualityCurvePoint> getCurvePoints();
}
