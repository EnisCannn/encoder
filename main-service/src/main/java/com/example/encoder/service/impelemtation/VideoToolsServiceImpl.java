package com.example.encoder.service.impelemtation;

import com.example.encoder.exception.ProcessingException;
import com.example.encoder.exception.NotFoundException;
import com.example.encoder.dto.CutRequest;
import com.example.encoder.entity.Video;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.VideoToolsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoToolsServiceImpl implements VideoToolsService {

    private final VideoRepository videoRepository;

    @Value("${encoder.folder.clips}")
    private String clipsFolder;

    @Value("${encoder.ffmpeg.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    // ffmpeg takilirsa istegi sonsuza kadar bloklamasin
    @Value("${encoder.ffmpeg.clip-timeout-seconds:600}")
    private long clipTimeoutSeconds;

    @Override
    public String cutVideo(CutRequest request) {
        Video video = videoRepository.findById(UUID.fromString(request.getVideoId()))
                .orElseThrow(() -> new NotFoundException("Video bulunamadı!"));

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
            log.info("Klip kesme başladı");

            // Cikti burada hic okunmuyordu: pipe tamponu dolarsa ffmpeg kilitlenirdi.
            com.example.encoder.util.FFmpegProcessRunner.Result result =
                    com.example.encoder.util.FFmpegProcessRunner.run(command, clipTimeoutSeconds);

            int exitCode = result.exitCode();
            log.info("Klip kesme bitti, çıkış kodu: {}", exitCode);

            if (exitCode == 0) {
                // GÜNCELLENDİ: Oluşan klibi veritabanına Video olarak kaydediyoruz
                Video clipVideo = new Video();
                clipVideo.setOriginalFileName(outputFileName);
                clipVideo.setStoredFileName(outputFileName);
                clipVideo.setPath(outputPath.toString());
                videoRepository.save(clipVideo);

                return outputFileName;
            } else {
                throw new ProcessingException("FFmpeg kesme işlemi başarısız oldu.");
            }
        } catch (Exception e) {
            throw new ProcessingException("Sistem hatası: " + e.getMessage(), e);
        }
    }
}