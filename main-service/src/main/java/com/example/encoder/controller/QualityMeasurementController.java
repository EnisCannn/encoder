package com.example.encoder.controller;

import com.example.encoder.dto.CalibrationSweepRequest;
import com.example.encoder.entity.QualityMeasurement;
import com.example.encoder.exception.BadRequestException;
import com.example.encoder.service.CalibrationSweepService;
import com.example.encoder.service.QualityMeasurementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityMeasurementController {

    private final QualityMeasurementService service;
    private final CalibrationSweepService sweepService;

    /**
     * Arayüzdeki satır bir paketi temsil ettiği için birden çok iş gönderilebiliyor;
     * tekli işte liste tek elemanlı olur.
     */
    @PostMapping("/measure")
    public ResponseEntity<List<QualityMeasurement>> measure(@RequestBody Map<String, List<UUID>> body) {
        List<UUID> jobIds = body.get("jobIds");
        if (jobIds == null || jobIds.isEmpty()) {
            throw new BadRequestException("jobIds boş olamaz.");
        }

        List<QualityMeasurement> queued = service.requestMeasurements(jobIds);
        if (queued.isEmpty()) {
            throw new BadRequestException("Ölçülebilecek tamamlanmış iş bulunamadı.");
        }
        return ResponseEntity.ok(queued);
    }

    @PostMapping("/measure/batch/{batchId}")
    public ResponseEntity<List<QualityMeasurement>> measureBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(service.requestForBatch(batchId));
    }

    /**
     * Kalibrasyon taraması başlatır: her bitrate için bir encode işi açılır,
     * işler bitince ölçümleri otomatik kuyruğa girer.
     */
    @PostMapping("/sweep")
    public ResponseEntity<Map<String, Integer>> startSweep(@Valid @RequestBody CalibrationSweepRequest request) {
        return ResponseEntity.ok(Map.of(
                "olusturulanIs", sweepService.startSweep(request).size()));
    }

    /** Kalite analizi grafiğinin verisi: tamamlanmış tüm ölçümler. */
    @GetMapping("/curves")
    public ResponseEntity<?> curves() {
        return ResponseEntity.ok(service.getCurvePoints());
    }

    /** Ayrıntılı sonuç (min / harmonik ortalama / hata mesajı) için. */
    @GetMapping
    public ResponseEntity<?> get(@RequestParam List<UUID> jobIds) {
        return ResponseEntity.ok(service.getMeasurements(jobIds));
    }
}
