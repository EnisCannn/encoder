package com.example.encoder.service.impelemtation;

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.service.MediaProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaProcessingServiceImpl implements MediaProcessingService {

    private final EncodingPresetRepository presetRepository;

    @Value("${encoder.folder.input}")
    private String inputFolder;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    @Override
    public String encodeVideo(String fileName, UUID presetId) {
        // 1. Veritabanından preset ayarlarını çek
        EncodingPreset preset = presetRepository.findById(presetId)
                .orElseThrow(() -> new RuntimeException("Preset bulunamadı!"));

        String inputPath = inputFolder + "/" + fileName;
        String outputPath = outputFolder + "/encoded_" + preset.getName() + "_" + fileName;

        try {
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-y");
            command.add("-i");
            command.add(inputPath);

            if (preset.getWidth() != null && preset.getHeight() != null) {
                command.add("-vf");
                command.add("scale=" + preset.getWidth() + ":" + preset.getHeight());
            }

            if (preset.getVideoBitrate() != null) {
                command.add("-b:v");
                command.add(preset.getVideoBitrate() + "000");
            }

            command.add(outputPath);

            System.out.println("=============================================");
            System.out.println("ARANAN DOSYA YOLU: " + inputPath);
            System.out.println("=============================================");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // FFmpeg'in ürettiği logları anlık okuyup tıkanmayı engelliyoruz
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[FFmpeg LOG]: " + line);
                }
            }

            // Artık loglar boşaltıldığı için FFmpeg rahatça bitip bu satıra ulaşabilecek
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return "GERÇEK VİDEO ENCODING BAŞARILI!\nÇıktı Konumu: " + outputPath;
            } else {
                return "FFmpeg dönüştürme sırasında hata kodu döndürdü: " + exitCode;
            }

        } catch (IOException | InterruptedException e) {
            return "Sistem hatası oluştu: " + e.getMessage();
        }
    }
}