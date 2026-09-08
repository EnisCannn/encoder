package com.example.encoder.service.impelemtation;

import com.example.encoder.entity.Video;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoRepository repository;

    @Value("${encoder.folder.input}")
    private String inputFolder;

    @Value("${encoder.ffmpeg.ffprobe-path:ffprobe}")
    private String ffprobePath;

    /** Kalibrasyon tarama formundaki secici icin: en yeni videolar basta. */
    @Override
    public List<Video> getAllVideos() {
        return repository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    @Override
    public Video uploadVideo(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String storedFileName = UUID.randomUUID().toString() + extension;

        Path filePath = Paths.get(inputFolder, storedFileName);
        Files.copy(file.getInputStream(), filePath);

        Video video = new Video();
        video.setOriginalFileName(originalFileName);
        video.setStoredFileName(storedFileName);
        video.setPath(filePath.toString());
        video.setSize(file.getSize());

        // Ajanı göreve çağırıyoruz!
        extractVideoMetadata(video, filePath.toString());

        return repository.save(video);
    }

    // AJANIN METODU (Nihai ve Tam Sürüm) - Dışarıdan görünmez, interface'e yazılmaz.
    private void extractVideoMetadata(Video video, String filePath) {
        try {
            // 1. Çözünürlüğü okuma (Genişlik ve Yükseklik)
            ProcessBuilder resBuilder = new ProcessBuilder(
                    ffprobePath, "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height", "-of", "csv=s=x:p=0", filePath);
            resBuilder.redirectErrorStream(true);
            Process resProcess = resBuilder.start();
            BufferedReader resReader = new BufferedReader(new InputStreamReader(resProcess.getInputStream()));
            String resolution = resReader.readLine();

            if (resolution != null && resolution.contains("x")) {
                String[] parts = resolution.split("x");
                video.setWidth(Integer.parseInt(parts[0].trim()));
                video.setHeight(Integer.parseInt(parts[1].trim()));
            }

            // 2. Süreyi okuma (Duration)
            ProcessBuilder durBuilder = new ProcessBuilder(
                    ffprobePath, "-v", "error", "-show_entries",
                    "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", filePath);
            durBuilder.redirectErrorStream(true);
            Process durProcess = durBuilder.start();
            BufferedReader durReader = new BufferedReader(new InputStreamReader(durProcess.getInputStream()));
            String duration = durReader.readLine();

            if (duration != null && !duration.trim().isEmpty()) {
                video.setDuration(new BigDecimal(duration.trim()));
            }

            // 3. Video Codec okuma (Örn: h264)
            ProcessBuilder vCodecBuilder = new ProcessBuilder(
                    ffprobePath, "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name", "-of", "default=noprint_wrappers=1:nokey=1", filePath);
            vCodecBuilder.redirectErrorStream(true);
            Process vCodecProcess = vCodecBuilder.start();
            BufferedReader vCodecReader = new BufferedReader(new InputStreamReader(vCodecProcess.getInputStream()));
            String videoCodec = vCodecReader.readLine();

            if (videoCodec != null && !videoCodec.trim().isEmpty()) {
                video.setVideoCodec(videoCodec.trim());
            }

            // 4. Audio Codec okuma (Örn: aac)
            ProcessBuilder aCodecBuilder = new ProcessBuilder(
                    ffprobePath, "-v", "error", "-select_streams", "a:0",
                    "-show_entries", "stream=codec_name", "-of", "default=noprint_wrappers=1:nokey=1", filePath);
            aCodecBuilder.redirectErrorStream(true);
            Process aCodecProcess = aCodecBuilder.start();
            BufferedReader aCodecReader = new BufferedReader(new InputStreamReader(aCodecProcess.getInputStream()));
            String audioCodec = aCodecReader.readLine();

            if (audioCodec != null && !audioCodec.trim().isEmpty()) {
                video.setAudioCodec(audioCodec.trim());
            }

        } catch (Exception e) {
            System.out.println("Metadata okuma hatası: " + e.getMessage());
        }
    }
}