package com.example.encoder.service; // Paket adını kontrol et

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.repository.EncodingPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final EncodingPresetRepository presetRepository;

    // Yöneticinin araştır dediği "Config Value" olayı tam olarak burası!
    // Bilgileri kodun içine yazmıyoruz, properties dosyasından vakumluyoruz.
    @Value("${encoder.folder.input}")
    private String inputFolder;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    public String encodeVideo(String fileName, UUID presetId) {
        // 1. Veritabanından dönüştürme ayarını (preset) bul
        EncodingPreset preset = presetRepository.findById(presetId)
                .orElseThrow(() -> new RuntimeException("Preset bulunamadı!"));

        // 2. Dosya yollarını birleştir (Hardcode yok!)
        String inputPath = inputFolder + "/" + fileName;
        String outputPath = outputFolder + "/encoded_" + preset.getName() + "_" + fileName;

        // 3. İşlem sonucunu (şimdilik simülasyon olarak) metin halinde dön
        return "İŞLEM BAŞARILI!\n" +
                "Alınan Dosya: " + inputPath + "\n" +
                "Uygulanan Ayar: " + preset.getName() + " (" + preset.getFormat() + ")\n" +
                "Çıktı Yeri: " + outputPath;
    }
}