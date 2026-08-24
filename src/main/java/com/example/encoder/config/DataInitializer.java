package com.example.encoder.config;

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;
import com.example.encoder.entity.enums.AudioCodec;
import com.example.encoder.repository.EncodingPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal; // <-- BigDecimal için gereken kütüphane eklendi

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EncodingPresetRepository repository;

    @Override
    public void run(String... args) throws Exception {
        // Veritabanında hiç kayıt yoksa 4 adet varsayılanı oluştur
        if (repository.count() == 0) {

            // 1. Preset: Standart 1080p
            EncodingPreset preset1 = new EncodingPreset();
            preset1.setName("Standart_1080p_MP4");
            preset1.setFormat(Format.MP4);
            preset1.setVideoCodec(VideoCodec.H264);
            preset1.setAudioCodec(AudioCodec.AAC);
            preset1.setWidth(1920);
            preset1.setHeight(1080);
            preset1.setVideoBitrate(5000);
            preset1.setAudioBitrate(192);
            preset1.setFrameRate(BigDecimal.valueOf(30.0)); // <-- BigDecimal formatına çevrildi
            preset1.setIsActive(true);

            // 2. Preset: Web İçin 720p (Güvenli Enum değerleri kullanıldı)
            EncodingPreset preset2 = new EncodingPreset();
            preset2.setName("Web_720p_Tasarruf");
            preset2.setFormat(Format.MP4);
            preset2.setVideoCodec(VideoCodec.H264);
            preset2.setAudioCodec(AudioCodec.AAC);
            preset2.setWidth(1280);
            preset2.setHeight(720);
            preset2.setVideoBitrate(2500);
            preset2.setAudioBitrate(128);
            preset2.setFrameRate(BigDecimal.valueOf(24.0)); // <-- BigDecimal formatına çevrildi
            preset2.setIsActive(true);

            // 3. Preset: Sinema 4K Ultra Kalite
            EncodingPreset preset3 = new EncodingPreset();
            preset3.setName("Sinema_4K_Ultra");
            preset3.setFormat(Format.MP4);
            preset3.setVideoCodec(VideoCodec.H264);
            preset3.setAudioCodec(AudioCodec.AAC);
            preset3.setWidth(3840);
            preset3.setHeight(2160);
            preset3.setVideoBitrate(15000);
            preset3.setAudioBitrate(320);
            preset3.setFrameRate(BigDecimal.valueOf(60.0)); // <-- BigDecimal formatına çevrildi
            preset3.setIsActive(true);

            // 4. Preset: Mobil 480p
            EncodingPreset preset4 = new EncodingPreset();
            preset4.setName("Mobil_480p_KotaDostu");
            preset4.setFormat(Format.MP4);
            preset4.setVideoCodec(VideoCodec.H264);
            preset4.setAudioCodec(AudioCodec.AAC);
            preset4.setWidth(854);
            preset4.setHeight(480);
            preset4.setVideoBitrate(1000);
            preset4.setAudioBitrate(96);
            preset4.setFrameRate(BigDecimal.valueOf(24.0)); // <-- BigDecimal formatına çevrildi
            preset4.setIsActive(true);

            // Hepsini veritabanına tek tek kaydet
            repository.save(preset1);
            repository.save(preset2);
            repository.save(preset3);
            repository.save(preset4);

            System.out.println("4 adet varsayılan preset veritabanına başarıyla eklendi!");
        }
    }
}