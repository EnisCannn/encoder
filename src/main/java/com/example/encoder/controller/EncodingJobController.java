package com.example.encoder.controller;

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.service.EncodingJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/encoding-jobs")
@RequiredArgsConstructor
public class EncodingJobController {

    private final EncodingJobService service;

    // 1. Dışarıdan yeni bir sipariş (Job) oluşturmak için
    @PostMapping
    public EncodingJob createJob(@Valid @RequestBody CreateEncodingJobRequest request) {
        return service.createJob(request);
    }

    // 2. Dışarıdan siparişin (Job) anlık durumunu sorgulamak için
    @GetMapping("/{id}")
    public EncodingJob getJob(@PathVariable UUID id) {
        return service.getJob(id);
    }
}