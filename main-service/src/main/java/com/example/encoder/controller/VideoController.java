package com.example.encoder.controller;

import com.example.encoder.entity.Video;
import com.example.encoder.service.impelemtation.VideoServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    @GetMapping("/play/{fileName}")
    public ResponseEntity<Resource> playVideo(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(outputFolder).resolve(fileName);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // inline özelliği videonun indirilmek yerine tarayıcıda oynatılmasını sağlar
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}