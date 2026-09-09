package com.example.encoder.repository;

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository // Spring'e bunun bir veritabanı depo sınıfı olduğunu söyler
public interface EncodingPresetRepository extends JpaRepository<EncodingPreset, UUID> {

    // JpaRepository sayesinde; save(), findAll(), findById(), deleteById() gibi
    // temel veritabanı komutlarının hepsi arka planda otomatik olarak hazırlandı!
// Sadece metod isimlerini yazıyoruz, Spring arka planda SQL sorgusunu kendi üretiyor!
    boolean existsByName(String name);

    /** Kalibrasyon taramasi ayni parametreli sablonu yeniden kullanmak icin ariyor. */
    Optional<EncodingPreset> findByName(String name);
    List<EncodingPreset> findByFormat(Format format);

    List<EncodingPreset> findByVideoCodec(VideoCodec videoCodec);
}