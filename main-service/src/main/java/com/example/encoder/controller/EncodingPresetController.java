package com.example.encoder.controller;

import com.example.encoder.dto.CreatePresetRequest;
import com.example.encoder.dto.PresetResponse;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.service.impelemtation.EncodingPresetServiceImpl;
import com.example.encoder.service.impelemtation.MediaProcessingServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/presets")
@RequiredArgsConstructor
public class EncodingPresetController {

    private final EncodingPresetServiceImpl service;
    private final MediaProcessingServiceImpl mediaService;

    // 1. CREATE - Artık dışarıdan CreatePresetRequest alıp PresetResponse dönüyor
    @PostMapping
    public PresetResponse createPreset(@Valid @RequestBody CreatePresetRequest request) {
        return service.createPreset(request);
    }

    // 2. READ - Listede de filtrelenmiş DTO listesi (PresetResponse) dönüyoruz
    @GetMapping
    public List<PresetResponse> getAllPresets() {
        return service.getAllPresets();
    }

    // 3. READ - Filtreli getirmede de DTO listesi dönüyoruz
    @GetMapping("/filter")
    public List<PresetResponse> getPresetsByFormat(@RequestParam Format format) {
        return service.getPresetsByFormat(format);
    }

    // 4. UPDATE - Güncellemede dışarıdan CreatePresetRequest alıp PresetResponse dönüyor
    @PutMapping("/{id}")
    public PresetResponse updatePreset(@PathVariable UUID id, @Valid @RequestBody CreatePresetRequest updatedData) {
        return service.updatePreset(id, updatedData);
    }

    // 5. DELETE - Silme işleminde veri dönmediği için değişiklik yok
    @DeleteMapping("/{id}")
    public void deletePreset(@PathVariable UUID id) {
        service.deletePreset(id);
    }

    // ENCODING SİMÜLASYONU - Dokunmuyoruz, tıkır tıkır çalışıyor
    @GetMapping("/encode")
    public String startEncoding(@RequestParam String fileName, @RequestParam UUID presetId) {
        return mediaService.encodeVideo(fileName, presetId);
    }
}