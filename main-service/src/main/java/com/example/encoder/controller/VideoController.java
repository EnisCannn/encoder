package com.example.encoder.controller;

import com.example.encoder.entity.Video;
import com.example.encoder.service.impelemtation.VideoServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoServiceImpl service;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    // POST /api/videos/upload endpoint'i
    @PostMapping("/upload")
    public Video uploadVideo(@RequestParam("file") MultipartFile file) throws IOException {
        return service.uploadVideo(file);
    }

    /**
     * Çıktı dosyasını oynatır.
     *
     * Paket (batch) çıktıları "<batchId>/720p_....mp4" gibi alt klasörde durduğu için
     * yol tek bir path değişkeniyle değil, /play/ sonrasının tamamıyla çözülüyor.
     * Tekli işlerdeki düz dosya adı da aynı şekilde çalışmaya devam eder.
     */
    @GetMapping("/play/**")
    public ResponseEntity<Resource> playVideo(HttpServletRequest request) {
        try {
            String relativePath = extractRelativePath(request);
            if (relativePath.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Path root = Paths.get(outputFolder).toAbsolutePath().normalize();
            Path filePath = root.resolve(relativePath).normalize();

            // Çıktı klasörünün dışına çıkan istekleri reddet (../ denemeleri)
            if (!filePath.startsWith(root)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(resolveContentType(filePath))
                    // inline özelliği videonun indirilmek yerine tarayıcıda oynatılmasını sağlar
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // GİZLİ METOT: /api/videos/play/ sonrasındaki yolu çözer
    private String extractRelativePath(HttpServletRequest request) {
        String path = new UrlPathHelper().getPathWithinApplication(request);
        String prefix = "/api/videos/play/";
        int index = path.indexOf(prefix);
        if (index < 0) {
            return "";
        }
        String remainder = path.substring(index + prefix.length());
        return URLDecoder.decode(remainder, StandardCharsets.UTF_8);
    }

    // GİZLİ METOT: Uzantıya göre içerik tipi.
    // .vtt yanlış tiple gönderilirse tarayıcı altyazıyı yüklemez, bu yüzden
    // işletim sisteminin tahminine bırakmadan açıkça eşliyoruz.
    private MediaType resolveContentType(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".vtt")) {
            return MediaType.parseMediaType("text/vtt");
        }
        if (name.endsWith(".m3u8")) {
            return MediaType.parseMediaType("application/vnd.apple.mpegurl");
        }
        if (name.endsWith(".ts")) {
            return MediaType.parseMediaType("video/mp2t");
        }
        if (name.endsWith(".smil")) {
            return MediaType.parseMediaType("application/smil+xml");
        }
        if (name.endsWith(".mp4") || name.endsWith(".m4s")) {
            return MediaType.parseMediaType("video/mp4");
        }

        try {
            String probed = Files.probeContentType(filePath);
            if (probed != null) {
                return MediaType.parseMediaType(probed);
            }
        } catch (IOException ignored) {
            // Tespit edilemezse mp4 varsayılanına düşülür
        }
        return MediaType.parseMediaType("video/mp4");
    }
}
