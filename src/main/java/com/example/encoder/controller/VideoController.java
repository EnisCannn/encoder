package com.example.encoder.controller;

import com.example.encoder.entity.Video;
import com.example.encoder.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService service;

    // POST /api/videos/upload endpoint'i
    @PostMapping("/upload")
    public Video uploadVideo(@RequestParam("file") MultipartFile file) throws IOException {
        return service.uploadVideo(file);
    }
}