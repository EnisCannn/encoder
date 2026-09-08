package com.example.encoder.entity;

public enum MeasurementStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    /** Olculemeyecek is: yakilmis altyazi, eksik kaynak dosya vb. */
    SKIPPED
}
