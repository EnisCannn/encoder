package com.example.encoder.controller;

import com.example.encoder.dto.CreateEncodeSetRequest;
import com.example.encoder.dto.EncodeSetResponse;
import com.example.encoder.service.impelemtation.EncodeSetServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/encode-sets")
@RequiredArgsConstructor
public class EncodeSetController {

    private final EncodeSetServiceImpl service;

    @PostMapping
    public EncodeSetResponse createSet(@Valid @RequestBody CreateEncodeSetRequest request) {
        return service.createSet(request);
    }

    @GetMapping
    public List<EncodeSetResponse> getAllSets() {
        return service.getAllSets();
    }

    @GetMapping("/{id}")
    public EncodeSetResponse getSet(@PathVariable UUID id) {
        return service.getSet(id);
    }

    @PutMapping("/{id}")
    public EncodeSetResponse updateSet(@PathVariable UUID id, @Valid @RequestBody CreateEncodeSetRequest request) {
        return service.updateSet(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteSet(@PathVariable UUID id) {
        service.deleteSet(id);
    }
}
