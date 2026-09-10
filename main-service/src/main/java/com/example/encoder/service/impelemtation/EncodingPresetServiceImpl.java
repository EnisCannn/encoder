package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.CreatePresetRequest;
import com.example.encoder.dto.PresetResponse;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.service.EncodingPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EncodingPresetServiceImpl implements EncodingPresetService {

    private final EncodingPresetRepository repository;

    // GİZLİ METOT: Dışarıdan çağrılamaz, interface'de yer almaz
    private PresetResponse mapToResponse(EncodingPreset entity) {
        return new PresetResponse(
                entity.getId(), entity.getName(), entity.getDescription(),
                entity.getFormat(), entity.getVideoCodec(), entity.getAudioCodec(),
                entity.getWidth(), entity.getHeight(), entity.getVideoBitrate(),
                entity.getAudioBitrate(), entity.getFrameRate(), entity.getIsActive(),
                Boolean.TRUE.equals(entity.getCalibration()),
                entity.getCreatedAt()
        );
    }

    // GİZLİ METOT: İş Kuralları Motoru
    private void validatePresetBusinessRules(CreatePresetRequest request, UUID currentId) {
        boolean nameExists = repository.existsByName(request.name());
        if (nameExists) {
            throw new RuntimeException("FAZ 4 KURAL İHLALİ: Bu isimde bir preset zaten var!");
        }

        if (request.format() == Format.MP4 && request.videoCodec() != VideoCodec.H264) {
            throw new RuntimeException("FAZ 4 KURAL İHLALİ: MP4 formatı projemizde şimdilik sadece H264 codec'ini destekler!");
        }

        if (request.audioBitrate() != null && (request.audioBitrate() < 64 || request.audioBitrate() > 320)) {
            throw new RuntimeException("FAZ 4 KURAL İHLALİ: Audio bitrate 64 ile 320 kbps arasında olmalıdır!");
        }
    }

    @Override
    public PresetResponse createPreset(CreatePresetRequest request) {
        validatePresetBusinessRules(request, null);

        EncodingPreset entity = new EncodingPreset();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setFormat(request.format());
        entity.setVideoCodec(request.videoCodec());
        entity.setAudioCodec(request.audioCodec());
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setVideoBitrate(request.videoBitrate());
        entity.setAudioBitrate(request.audioBitrate());
        entity.setFrameRate(request.frameRate());
        entity.setIsActive(true);

        return mapToResponse(repository.save(entity));
    }

    @Override
    public PresetResponse updatePreset(UUID id, CreatePresetRequest request) {
        EncodingPreset entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kayıt bulunamadı!"));

        if (!entity.getName().equals(request.name())) {
            validatePresetBusinessRules(request, id);
        } else {
            if (request.format() == Format.MP4 && request.videoCodec() != VideoCodec.H264) {
                throw new RuntimeException("FAZ 4 KURAL İHLALİ: MP4 formatı şimdilik sadece H264 destekler!");
            }
            if (request.audioBitrate() != null && (request.audioBitrate() < 64 || request.audioBitrate() > 320)) {
                throw new RuntimeException("FAZ 4 KURAL İHLALİ: Audio bitrate 64 ile 320 kbps arasında olmalıdır!");
            }
        }

        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setFormat(request.format());
        entity.setVideoCodec(request.videoCodec());
        entity.setAudioCodec(request.audioCodec());
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setVideoBitrate(request.videoBitrate());
        entity.setAudioBitrate(request.audioBitrate());
        entity.setFrameRate(request.frameRate());

        return mapToResponse(repository.save(entity));
    }

    @Override
    public List<PresetResponse> getAllPresets() {
        // Kalibrasyon sablonlari da donuyor. Onceden filtreleniyorlardi ve bu
        // yaniltiyordu: arayuzde 14 sablon gorunurken veritabaninda 164 kayit
        // vardi. Artik hepsi geliyor, ayrimi calibration bayragiyla arayuz yapiyor.
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PresetResponse> getPresetsByFormat(Format format) {
        return repository.findByFormat(format).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public void deletePreset(UUID id) {
        repository.deleteById(id);
    }
}