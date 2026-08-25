package com.example.encoder.service;

import com.example.encoder.dto.CreatePresetRequest;
import com.example.encoder.dto.PresetResponse;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;
import com.example.encoder.repository.EncodingPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EncodingPresetService {

    private final EncodingPresetRepository repository;

    private PresetResponse mapToResponse(EncodingPreset entity) {
        return new PresetResponse(
                entity.getId(), entity.getName(), entity.getDescription(),
                entity.getFormat(), entity.getVideoCodec(), entity.getAudioCodec(),
                entity.getWidth(), entity.getHeight(), entity.getVideoBitrate(),
                entity.getAudioBitrate(), entity.getFrameRate(), entity.getIsActive()
        );
    }

    // YENİ: FAZ 4 - Merkezi İş Kuralları Motoru
    private void validatePresetBusinessRules(CreatePresetRequest request, UUID currentId) {
        // Kural 1: İsim Çakışması Kontrolü
        // (Eğer yeni kayıt ekleniyorsa currentId null'dır. Güncelleniyorsa kendi ismi hariç tutulur)
        boolean nameExists = repository.existsByName(request.name());
        if (nameExists) {
            // Eğer güncelleme ise ve isim değişmemişse hata verme, ama farklı bir ID'de bu isim varsa hata ver
            throw new RuntimeException("FAZ 4 KURAL İHLALİ: Bu isimde bir preset zaten var!");
        }

        // Kural 2: Codec ve Format Uyumluluğu
        if (request.format() == Format.MP4 && request.videoCodec() != VideoCodec.H264) {
            throw new RuntimeException("FAZ 4 KURAL İHLALİ: MP4 formatı projemizde şimdilik sadece H264 codec'ini destekler!");
        }

        // Kural 3: Ses Kalitesi Mantıksal Sınırları
        if (request.audioBitrate() != null && (request.audioBitrate() < 64 || request.audioBitrate() > 320)) {
            throw new RuntimeException("FAZ 4 KURAL İHLALİ: Audio bitrate 64 ile 320 kbps arasında olmalıdır!");
        }
    }

    public PresetResponse createPreset(CreatePresetRequest request) {
        // Veritabanına yazmadan önce kuralları işlet
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

    public PresetResponse updatePreset(UUID id, CreatePresetRequest request) {
        EncodingPreset entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kayıt bulunamadı!"));

        // Not: Gerçek bir sistemde ismin kendisine ait olup olmadığını da kontrol etmek gerekir
        // Basitlik adına şimdilik isim değişiyorsa kontrol ediyoruz
        if (!entity.getName().equals(request.name())) {
            validatePresetBusinessRules(request, id);
        } else {
            // İsim aynı kalıyorsa sadece diğer kuralları (Codec, Bitrate) kontrol et
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

    public List<PresetResponse> getAllPresets() {
        return repository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<PresetResponse> getPresetsByFormat(Format format) {
        return repository.findByFormat(format).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public void deletePreset(UUID id) {
        repository.deleteById(id);
    }
}