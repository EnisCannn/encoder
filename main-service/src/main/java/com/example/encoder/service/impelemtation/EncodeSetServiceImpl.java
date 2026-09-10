package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.CreateEncodeSetRequest;
import com.example.encoder.dto.EncodeSetResponse;
import com.example.encoder.dto.PresetResponse;
import com.example.encoder.entity.EncodeSet;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.repository.EncodeSetRepository;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.service.EncodeSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EncodeSetServiceImpl implements EncodeSetService {

    private final EncodeSetRepository repository;
    private final EncodingPresetRepository presetRepository;

    // GIZLI METOT: Entity -> DTO cevrimi
    private EncodeSetResponse mapToResponse(EncodeSet entity) {
        List<PresetResponse> presets = entity.getPresets().stream()
                .map(p -> new PresetResponse(
                        p.getId(), p.getName(), p.getDescription(),
                        p.getFormat(), p.getVideoCodec(), p.getAudioCodec(),
                        p.getWidth(), p.getHeight(), p.getVideoBitrate(),
                        p.getAudioBitrate(), p.getFrameRate(), p.getIsActive(),
                        Boolean.TRUE.equals(p.getCalibration()), p.getCreatedAt()))
                .collect(Collectors.toList());

        return new EncodeSetResponse(
                entity.getId(), entity.getName(), entity.getDescription(),
                presets, entity.getIsActive(), entity.getCreatedAt()
        );
    }

    /**
     * Istekte gelen ID sirasini koruyarak presetleri getirir.
     * Sira onemli: SMIL ve HLS master playlist varyantlari bu sirayla yazilacak.
     */
    private List<EncodingPreset> resolvePresets(List<UUID> presetIds) {
        List<EncodingPreset> presets = new ArrayList<>();
        for (UUID presetId : presetIds) {
            EncodingPreset preset = presetRepository.findById(presetId)
                    .orElseThrow(() -> new RuntimeException("Preset bulunamadi: " + presetId));
            if (presets.contains(preset)) {
                throw new RuntimeException("Ayni preset pakete iki kez eklenemez: " + preset.getName());
            }
            presets.add(preset);
        }
        return presets;
    }

    @Override
    @Transactional
    public EncodeSetResponse createSet(CreateEncodeSetRequest request) {
        if (repository.existsByName(request.name())) {
            throw new RuntimeException("Bu isimde bir paket zaten var: " + request.name());
        }

        EncodeSet entity = new EncodeSet();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPresets(resolvePresets(request.presetIds()));
        entity.setIsActive(true);

        return mapToResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public EncodeSetResponse updateSet(UUID id, CreateEncodeSetRequest request) {
        EncodeSet entity = repository.findByIdWithPresets(id)
                .orElseThrow(() -> new RuntimeException("Paket bulunamadi!"));

        if (!entity.getName().equals(request.name()) && repository.existsByName(request.name())) {
            throw new RuntimeException("Bu isimde bir paket zaten var: " + request.name());
        }

        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPresets(resolvePresets(request.presetIds()));
        entity.setUpdatedAt(Instant.now());

        return mapToResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public EncodeSetResponse getSet(UUID id) {
        return repository.findByIdWithPresets(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Paket bulunamadi!"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EncodeSetResponse> getAllSets() {
        return repository.findAllWithPresets().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteSet(UUID id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Paket bulunamadi!");
        }
        repository.deleteById(id);
    }
}
