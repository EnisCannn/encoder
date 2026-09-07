package com.example.encoder.service;

import com.example.encoder.dto.CreateEncodeSetRequest;
import com.example.encoder.dto.EncodeSetResponse;

import java.util.List;
import java.util.UUID;

public interface EncodeSetService {
    EncodeSetResponse createSet(CreateEncodeSetRequest request);
    EncodeSetResponse updateSet(UUID id, CreateEncodeSetRequest request);
    EncodeSetResponse getSet(UUID id);
    List<EncodeSetResponse> getAllSets();
    void deleteSet(UUID id);
}
