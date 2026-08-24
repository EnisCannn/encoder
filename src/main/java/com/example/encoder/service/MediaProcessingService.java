package com.example.encoder.service;

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.repository.EncodingPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final EncodingPresetRepository presetRepository;

    @Value("${encoder.folder.input}")
    private String inputFolder;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    public String encodeVideo(String fileName, UUID presetId) {
        // 1. Veritabanından preset ayarlarını çek
        EncodingPreset preset = presetRepository.findById(presetId)
                .orElseThrow(() -> new RuntimeException("Preset bulunamadı!"));

        String inputPath = inputFolder + "/" + fileName;
        String outputPath = outputFolder + "/encoded_" + preset.getName() + "_" + fileName;

        try {
            // 2. FFmpeg komut satırı yapısını oluşturalım
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-y"); // Aynı isimde dosya varsa üzerine yaz
            command.add("-i");
            command.add(inputPath);

            // Çözünürlük (Width x Height) ayarı varsa ekle
            if (preset.getWidth() != null && preset.getHeight() != null) {
                command.add("-vf");
                command.add("scale=" + preset.getWidth() + ":" + preset.getHeight());
            }

            // Video Bitrate ayarı varsa ekle (örn: 5000k)
            if (preset.getVideoBitrate() != null) {
                command.add("-b:v");
                command.add(preset.getVideoBitrate() + "000"); // kbps cinsinden
            }

            // Çıkış dosya yolu
            command.add(outputPath);

            // 3. İşletim sisteminde komutu çalıştır
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // İşlemin tamamlanmasını bekle
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return "✅ GERÇEK VİDEO ENCODING BAŞARILI!\n" +
                        "İşlenen Dosya: " + inputPath + "\n" +
                        "Kullanılan Preset: " + preset.getName() + "\n" +
                        "Çıktı Konumu: " + outputPath;
            } else {
                return "FFmpeg dönüştürme sırasında hata kodu döndürdü: " + exitCode;
            }

        } catch (IOException | InterruptedException e) {
            return "Sistem hatası oluştu: " + e.getMessage();
        }
    }
}