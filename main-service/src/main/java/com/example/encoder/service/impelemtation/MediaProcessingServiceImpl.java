package com.example.encoder.service.impelemtation;

import com.example.encoder.exception.NotFoundException;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.service.MediaProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessingServiceImpl implements MediaProcessingService {

    private final EncodingPresetRepository presetRepository;

    @Value("${encoder.folder.input}")
    private String inputFolder;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    // ffmpeg takilirsa istegi sonsuza kadar bloklamasin
    @Value("${encoder.ffmpeg.encode-timeout-seconds:7200}")
    private long encodeTimeoutSeconds;

    @Override
    public String encodeVideo(String fileName, UUID presetId) {
        // 1. Veritabanından preset ayarlarını çek
        EncodingPreset preset = presetRepository.findById(presetId)
                .orElseThrow(() -> new NotFoundException("Preset bulunamadı!"));

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

            log.debug("Aranan dosya yolu: {}", inputPath);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            com.example.encoder.util.FFmpegProcessRunner.Result result =
                    com.example.encoder.util.FFmpegProcessRunner.run(
                            processBuilder.command(), encodeTimeoutSeconds,
                            line -> log.debug("[FFmpeg LOG]: {}", line));

            int exitCode = result.exitCode();

            if (exitCode == 0) {
                return "GERÇEK VİDEO ENCODING BAŞARILI!\nÇıktı Konumu: " + outputPath;
            } else {
                return "FFmpeg dönüştürme sırasında hata kodu döndürdü: " + exitCode;
            }

        } catch (Exception e) {
            return "Sistem hatası oluştu: " + e.getMessage();
        }
    }
}