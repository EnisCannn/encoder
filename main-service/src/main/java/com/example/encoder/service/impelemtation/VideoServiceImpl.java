package com.example.encoder.service.impelemtation;

import com.example.encoder.entity.Video;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.VideoService;
import com.example.encoder.util.FFmpegProcessRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    /** Saglam bir dosyada ffprobe saniyeler icinde biter; takilirsa istek bekletilmez. */
    private static final long FFPROBE_TIMEOUT_SECONDS = 60;

    private final VideoRepository repository;
    private final JsonMapper jsonMapper;

    @Value("${encoder.folder.input}")
    private String inputFolder;

    @Value("${encoder.ffmpeg.ffprobe-path:ffprobe}")
    private String ffprobePath;

    /** Kalibrasyon tarama formundaki secici icin: en yeni videolar basta. */
    @Override
    public List<Video> getAllVideos() {
        return repository.findAllByOrderByCreatedAtDesc();
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

    /**
     * Worker'in ilerleme hesabi Video.duration'a bakiyor; o yuzden CMS'in
     * dosyasi da yuklenen dosyalar gibi ffprobe'dan geciriliyor.
     */
    @Override
    public Video registerExisting(Path path) {
        Video video = new Video();
        video.setOriginalFileName(path.getFileName().toString());
        video.setStoredFileName(path.getFileName().toString());
        video.setPath(path.toString());
        try {
            video.setSize(Files.size(path));
        } catch (IOException e) {
            // Boyut yalnizca bilgi amacli; okunamazsa encode yine yapilabilir
        }

        extractVideoMetadata(video, path.toString());

        return repository.save(video);
    }


    /**
     * Cozunurluk, sure ve codec bilgilerini tek ffprobe cagrisiyla JSON olarak okur.
     * Onceden dort ayri surec aciliyordu; ortak calistirici sayesinde artik sure
     * asiminda surec olduruluyor ve cikti akisi her zaman kapatiliyor.
     * Metadata yalnizca bilgi amacli: okunamazsa video yine kaydedilir.
     */
    private void extractVideoMetadata(Video video, String filePath) {
        // Yalnizca gereken alanlar isteniyor; cok akisli dosyalarda bile cikti kucuk kalir
        List<String> command = List.of(ffprobePath, "-v", "error",
                "-show_entries", "stream=codec_type,codec_name,width,height:format=duration",
                "-of", "json", filePath);
        try {
            FFmpegProcessRunner.Result result = FFmpegProcessRunner.run(command, FFPROBE_TIMEOUT_SECONDS);
            if (!result.isSuccess()) {
                log.warn("ffprobe metadata okuyamadi ({}): {}", filePath, result.output().trim());
                return;
            }
            applyMetadata(video, jsonMapper.readTree(result.output()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Metadata okuma kesildi: {}", filePath);
        } catch (Exception e) {
            log.warn("Metadata okuma hatasi ({}): {}", filePath, e.getMessage());
        }
    }

    private void applyMetadata(Video video, JsonNode probe) {
        String duration = probe.path("format").path("duration").asString("");
        if (!duration.isBlank()) {
            video.setDuration(new BigDecimal(duration.trim()));
        }

        boolean videoFound = false;
        boolean audioFound = false;
        for (JsonNode stream : probe.path("streams")) {
            String type = stream.path("codec_type").asString("");
            // Eskisi gibi ilk video (v:0) ve ilk ses (a:0) akisi esas aliniyor
            if ("video".equals(type) && !videoFound) {
                videoFound = true;
                video.setVideoCodec(stream.path("codec_name").asString(null));
                if (stream.has("width") && stream.has("height")) {
                    video.setWidth(stream.path("width").asInt());
                    video.setHeight(stream.path("height").asInt());
                }
            } else if ("audio".equals(type) && !audioFound) {
                audioFound = true;
                video.setAudioCodec(stream.path("codec_name").asString(null));
            }
        }
    }
}
