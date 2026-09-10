package com.example.encoder.controller;

import com.example.encoder.dto.LiveClipRequest;
import com.example.encoder.dto.LiveStreamRequest;
import com.example.encoder.entity.LiveStream;
import com.example.encoder.service.LiveStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveStreamController {

    private final LiveStreamService liveStreamService;

    @Value("${encoder.folder.live}")
    private String liveBaseFolder;

    @GetMapping
    public ResponseEntity<List<LiveStream>> getAllStreams() {
        try {
            return ResponseEntity.ok(liveStreamService.getAllStreams());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/play/{streamId}/{fileName:.+}")
    public ResponseEntity<Resource> playStreamFile(@PathVariable String streamId, @PathVariable String fileName) {
        try {
            Path filePath = Paths.get(liveBaseFolder, streamId, fileName);
            Resource resource = new UrlResource(filePath.toUri());

            if (fileName.equals("index.m3u8") && (!resource.exists() || !resource.isReadable())) {
                filePath = Paths.get(liveBaseFolder, streamId, "index.m3u8.tmp");
                resource = new UrlResource(filePath.toUri());
            }

            if (resource.exists() || resource.isReadable()) {
                String contentType = "application/octet-stream";
                if (fileName.contains(".m3u8")) {
                    contentType = "application/vnd.apple.mpegurl";
                } else if (fileName.endsWith(".ts")) {
                    contentType = "video/MP2T";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/start")
    public ResponseEntity<LiveStream> startStream(@RequestBody LiveStreamRequest request) {
        try {
            LiveStream stream = liveStreamService.startStream(request);
            return ResponseEntity.ok(stream);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/stop/{id}")
    public ResponseEntity<String> stopStream(@PathVariable UUID id) {
        try {
            liveStreamService.stopStream(id);
            return ResponseEntity.ok("Yayın başarıyla durduruldu.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }

    @PostMapping("/clip")
    public ResponseEntity<String> createLiveClip(@RequestBody LiveClipRequest request) {
        try {
            String fileName = liveStreamService.createLiveClip(request);
            return ResponseEntity.ok("Klip alındı: " + fileName);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteStream(@PathVariable UUID id) {
        try {
            liveStreamService.deleteStream(id);
            return ResponseEntity.ok("Yayın kaydı silindi.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }
}