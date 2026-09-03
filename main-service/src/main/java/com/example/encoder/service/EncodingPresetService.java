package com.example.encoder.service;

import com.example.encoder.dto.CreatePresetRequest;
import com.example.encoder.dto.PresetResponse;
import com.example.encoder.entity.enums.Format;

import java.util.List;
import java.util.UUID;

public interface EncodingPresetService {
    PresetResponse createPreset(CreatePresetRequest request);
    PresetResponse updatePreset(UUID id, CreatePresetRequest request);
    List<PresetResponse> getAllPresets();
    List<PresetResponse> getPresetsByFormat(Format format);
    void deletePreset(UUID id);
}