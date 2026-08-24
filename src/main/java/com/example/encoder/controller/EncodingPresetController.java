package com.example.encoder.controller; // Kendi paket adına göre kontrol et

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.service.EncodingPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController // Bu sınıfın bir web API gişesi olduğunu belirtir
@RequestMapping("/api/presets") // Bu sınıftaki tüm adreslerin "/api/presets" ile başlamasını sağlar
@RequiredArgsConstructor
public class EncodingPresetController {

    private final EncodingPresetService service; // Müdürümüzü (Service) buraya çağırıyoruz

    // 1. CREATE - Yeni kayıt ekleme (POST isteği)
    @PostMapping
    public EncodingPreset createPreset(@RequestBody EncodingPreset preset) {
        return service.createPreset(preset);
    }

    // 2. READ - Hepsini getirme (GET isteği)
    @GetMapping
    public List<EncodingPreset> getAllPresets() {
        return service.getAllPresets();
    }

    // 3. READ - Filtreli getirme (GET isteği -> /api/presets/filter?format=MP4)
    @GetMapping("/filter")
    public List<EncodingPreset> getPresetsByFormat(@RequestParam Format format) {
        return service.getPresetsByFormat(format);
    }

    // 4. UPDATE - Güncelleme (PUT isteği -> /api/presets/{id})
    @PutMapping("/{id}")
    public EncodingPreset updatePreset(@PathVariable UUID id, @RequestBody EncodingPreset updatedData) {
        return service.updatePreset(id, updatedData);
    }

    // 5. DELETE - Silme (DELETE isteği -> /api/presets/{id})
    @DeleteMapping("/{id}")
    public void deletePreset(@PathVariable UUID id) {
        service.deletePreset(id);
    }
}