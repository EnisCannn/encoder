package com.example.encoder.controller;

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.service.impelemtation.EncodingJobServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/encoding-jobs")
@RequiredArgsConstructor
public class EncodingJobController {

    private final EncodingJobServiceImpl service;

    @Value("${encoder.folder.assets}")
    private String assetsFolder;

    // Arayüzden (Angular) gelen Form verilerini ve dosyaları karşılar
    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public EncodingJob createJob(
            @Valid @ModelAttribute CreateEncodingJobRequest request,
            @RequestParam(value = "subtitleFile", required = false) MultipartFile subtitleFile,
            @RequestParam(value = "dubbingFile", required = false) MultipartFile dubbingFile) {

        try {
            // Paylaşılan volume altındaki klasör yoksa oluştur
            Path assetsDir = Paths.get(assetsFolder);
            Files.createDirectories(assetsDir);

            // Seçilen altyazı dosyası varsa sunucuya kaydet ve yolunu DTO'ya set et
            if (subtitleFile != null && !subtitleFile.isEmpty()) {
                Path subPath = assetsDir.resolve("sub_" + UUID.randomUUID() + "_" + subtitleFile.getOriginalFilename());
                Files.copy(subtitleFile.getInputStream(), subPath, StandardCopyOption.REPLACE_EXISTING);
                request.setSubtitlePath(subPath.toAbsolutePath().toString());
            }

            // Seçilen dublaj dosyası varsa sunucuya kaydet ve yolunu DTO'ya set et
            if (dubbingFile != null && !dubbingFile.isEmpty()) {
                Path dubPath = assetsDir.resolve("dub_" + UUID.randomUUID() + "_" + dubbingFile.getOriginalFilename());
                Files.copy(dubbingFile.getInputStream(), dubPath, StandardCopyOption.REPLACE_EXISTING);
                request.setDubbingPath(dubPath.toAbsolutePath().toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Dosya yükleme hatası: " + e.getMessage());
        }

        // Değerler eklendikten sonra eski servisine dokunmadan gönderilir
        return service.createJob(request);
    }

    @GetMapping("/{id}")
    public EncodingJob getJob(@PathVariable UUID id) {
        return service.getJob(id);
    }

    @GetMapping
    public List<EncodingJob> getAllJobs() {
        return service.getAllJobs();
    }

    @DeleteMapping("/{id}")
    public void deleteJob(@PathVariable UUID id) {
        service.deleteJob(id);
    }
}