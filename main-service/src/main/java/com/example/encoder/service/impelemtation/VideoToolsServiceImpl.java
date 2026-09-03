package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.CutRequest;
import com.example.encoder.entity.Video;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.VideoToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoToolsServiceImpl implements VideoToolsService {

    private final VideoRepository videoRepository;

    @Value("${encoder.folder.clips}")
    private String clipsFolder;

    @Value("${encoder.ffmpeg.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Override
    public String cutVideo(CutRequest request) {
        Video video = videoRepository.findById(UUID.fromString(request.getVideoId()))
                .orElseThrow(() -> new RuntimeException("Video bulunamadı!"));

        // Klasör yoksa otomatik oluştur
        File dir = new File(clipsFolder);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String outputFileName = "cut_" + System.currentTimeMillis() + "_" + video.getStoredFileName();
        Path outputPath = Paths.get(clipsFolder, outputFileName);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");

        command.add("-ss");
        command.add(request.getStartTime());

        command.add("-to");
        command.add(request.getEndTime());

        command.add("-i");
        command.add(video.getPath());

        command.add("-c");
        command.add("copy");
        command.add(outputPath.toString());

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            System.out.println("--- KLİP KESME İŞLEMİ BAŞLADI ---");
            Process process = builder.start();

            int exitCode = process.waitFor();
            System.out.println("--- KLİP KESME BİTTİ. Çıkış Kodu: " + exitCode + " ---");

            if (exitCode == 0) {
                // GÜNCELLENDİ: Oluşan klibi veritabanına Video olarak kaydediyoruz
                Video clipVideo = new Video();
                clipVideo.setOriginalFileName(outputFileName);
                clipVideo.setStoredFileName(outputFileName);
                clipVideo.setPath(outputPath.toString());
                videoRepository.save(clipVideo);

                return outputFileName;
            } else {
                throw new RuntimeException("FFmpeg kesme işlemi başarısız oldu.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Sistem hatası: " + e.getMessage());
        }
    }
}