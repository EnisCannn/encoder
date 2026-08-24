package com.example.encoder.service; // Paket adını kendi projene göre kontrol etmeyi unutma

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.repository.EncodingPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncodingPresetService {

    private final EncodingPresetRepository repository;

    // 1. CREATE (Oluşturma)
    public EncodingPreset createPreset(EncodingPreset preset) {
        return repository.save(preset);
    }

    // 2. READ - Hepsini Getirme
    public List<EncodingPreset> getAllPresets() {
        return repository.findAll();
    }

    // 3. READ - Filtreli Getirme (Format değerine göre)
    public List<EncodingPreset> getPresetsByFormat(Format format) {
        return repository.findByFormat(format);
    }

    // 4. UPDATE (Güncelleme)
    public EncodingPreset updatePreset(UUID id, EncodingPreset updatedData) {
        // Önce veritabanında bu ID'ye sahip bir kayıt var mı diye bakıyoruz
        EncodingPreset existingPreset = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Güncellenmek istenen kayıt bulunamadı!"));

        // Varsa, dışarıdan gelen yeni verileri eski kaydın üzerine yazıyoruz
        existingPreset.setName(updatedData.getName());
        existingPreset.setDescription(updatedData.getDescription());
        existingPreset.setFormat(updatedData.getFormat());
        existingPreset.setVideoCodec(updatedData.getVideoCodec());
        existingPreset.setAudioCodec(updatedData.getAudioCodec());
        existingPreset.setContainer(updatedData.getContainer());
        existingPreset.setWidth(updatedData.getWidth());
        existingPreset.setHeight(updatedData.getHeight());
        existingPreset.setVideoBitrate(updatedData.getVideoBitrate());
        existingPreset.setAudioBitrate(updatedData.getAudioBitrate());
        existingPreset.setFrameRate(updatedData.getFrameRate());
        existingPreset.setPresetProfile(updatedData.getPresetProfile());
        existingPreset.setCrf(updatedData.getCrf());
        existingPreset.setAudioSampleRate(updatedData.getAudioSampleRate());
        existingPreset.setAudioChannels(updatedData.getAudioChannels());
        existingPreset.setIsActive(updatedData.getIsActive());

        // Güncellenmiş nesneyi veritabanına tekrar kaydediyoruz
        return repository.save(existingPreset);
    }

    // 5. DELETE (Silme)
    public void deletePreset(UUID id) {
        // Doğrudan ID'ye göre silme işlemi yapar
        repository.deleteById(id);
    }
}