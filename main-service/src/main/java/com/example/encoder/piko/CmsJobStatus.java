package com.example.encoder.piko;

import com.example.encoder.entity.JobStatus;

import java.time.Instant;

/**
 * Bir CMS isinin, arkasindaki EncodingJob'lardan hesaplanan ozet durumu.
 * SOAP adapter'i bunu CMS'in bekledigi cevap bicimine cevirir.
 *
 * @param completedAt yalnizca COMPLETED'da dolu: son profilin bittigi an
 */
public record CmsJobStatus(JobStatus status, int progress, String errorMessage, Instant completedAt) {
}
