package com.example.encoder.controller;

import com.example.encoder.dto.CalibrationSweepRequest;
import com.example.encoder.entity.QualityMeasurement;
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
    public ResponseEntity<?> measure(@RequestBody Map<String, List<UUID>> body) {
        try {
            List<UUID> jobIds = body.get("jobIds");
            if (jobIds == null || jobIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("hata", "jobIds boş olamaz."));
            }

            List<QualityMeasurement> queued = service.requestMeasurements(jobIds);
            if (queued.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "hata", "Ölçülebilecek tamamlanmış iş bulunamadı."));
            }
            return ResponseEntity.ok(queued);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("hata", e.getMessage()));
        }
    }

    @PostMapping("/measure/batch/{batchId}")
    public ResponseEntity<?> measureBatch(@PathVariable UUID batchId) {
        try {
            return ResponseEntity.ok(service.requestForBatch(batchId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("hata", e.getMessage()));
        }
    }

    /**
     * Kalibrasyon taraması başlatır: her bitrate için bir encode işi açılır,
     * işler bitince ölçümleri otomatik kuyruğa girer.
     */
    @PostMapping("/sweep")
    public ResponseEntity<?> startSweep(@Valid @RequestBody CalibrationSweepRequest request) {
        try {
            return ResponseEntity.ok(Map.of(
                    "olusturulanIs", sweepService.startSweep(request).size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("hata", e.getMessage()));
        }
    }

    /** Kalite analizi grafiğinin verisi: tamamlanmış tüm ölçümler. */
    @GetMapping("/curves")
    public ResponseEntity<?> curves() {
        try {
            return ResponseEntity.ok(service.getCurvePoints());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("hata", e.getMessage()));
        }
    }

    /** Ayrıntılı sonuç (min / harmonik ortalama / hata mesajı) için. */
    @GetMapping
    public ResponseEntity<?> get(@RequestParam List<UUID> jobIds) {
        try {
            return ResponseEntity.ok(service.getMeasurements(jobIds));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("hata", e.getMessage()));
        }
    }
}
