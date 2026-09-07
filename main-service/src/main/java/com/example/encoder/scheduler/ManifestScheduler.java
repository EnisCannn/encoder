package com.example.encoder.scheduler;

import com.example.encoder.entity.JobStatus;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.service.HlsPackagerService;
import com.example.encoder.service.SmilGeneratorService;
import com.example.encoder.service.impelemtation.HlsPackagerServiceImpl;
import com.example.encoder.service.impelemtation.SmilGeneratorServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Paketin son işi de bitince manifestleri üretir.
 *
 * Tetikleme worker'da değil burada: iki worker aynı anda son işi bitirdiğinde
 * ikisi de "paket tamamlandı" görüp manifesti iki kez üretmeye kalkardı.
 * main-service tek kopya çalıştığı için böyle bir yarış oluşmuyor.
 */
@Component
@RequiredArgsConstructor
public class ManifestScheduler {

    private final EncodingJobRepository jobRepository;
    private final SmilGeneratorService smilGeneratorService;
    private final HlsPackagerService hlsPackagerService;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    /**
     * VOD tarafında oynatma SMIL üzerinden yapılıyor, HLS varyantlarına gerek yok.
     * Segmentler mp4'lerin bir kopyası kadar yer kapladığı için varsayılan kapalı;
     * ihtiyaç olursa encoder.manifest.hls-enabled=true ile geri açılır.
     * (Canlı yayın tarafı bundan etkilenmez, o kendi HLS'ini üretmeye devam eder.)
     */
    @Value("${encoder.manifest.hls-enabled:false}")
    private boolean hlsEnabled;

    @Scheduled(fixedDelayString = "${encoder.manifest.check-interval-ms:5000}")
    public void generateMissingManifests() {
        List<UUID> batchIds = jobRepository.findFullyCompletedBatchIds(JobStatus.COMPLETED);

        for (UUID batchId : batchIds) {
            Path batchDir = Paths.get(outputFolder).resolve(batchId.toString());

            Path smil = batchDir.resolve(SmilGeneratorServiceImpl.SMIL_FILE_NAME);
            if (!Files.exists(smil)) {
                try {
                    smilGeneratorService.generate(batchId);
                    System.out.println("SMIL üretildi: " + smil);
                } catch (Exception e) {
                    System.out.println("SMIL üretilemedi (paket " + batchId + "): " + e.getMessage());
                }
            }

            if (!hlsEnabled) {
                continue;
            }

            Path master = batchDir
                    .resolve(HlsPackagerServiceImpl.HLS_DIR)
                    .resolve(HlsPackagerServiceImpl.MASTER_FILE_NAME);
            if (!Files.exists(master)) {
                try {
                    hlsPackagerService.packageBatch(batchId);
                    System.out.println("HLS master üretildi: " + master);
                } catch (Exception e) {
                    System.out.println("HLS paketlenemedi (paket " + batchId + "): " + e.getMessage());
                }
            }
        }
    }
}
