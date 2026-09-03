package com.example.encoder.controller;

import com.example.encoder.dto.CutRequest;
import com.example.encoder.service.VideoToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/video-tools")
@RequiredArgsConstructor
public class VideoToolsController {

    private final VideoToolsService toolsService;

    @PostMapping("/cut")
    public ResponseEntity<String> cutVideo(@RequestBody CutRequest request) {
        try {
            String newFileName = toolsService.cutVideo(request);
            return ResponseEntity.ok("Klip başarıyla oluşturuldu: " + newFileName);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }
}