package com.example.encoderservice.repository;

import com.example.encoderservice.entity.EncodingPreset;
import com.example.encoderservice.entity.enums.Format;
import com.example.encoderservice.entity.enums.VideoCodec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository // Spring'e bunun bir veritabanı depo sınıfı olduğunu söyler
public interface EncodingPresetRepository extends JpaRepository<EncodingPreset, UUID> {

    // JpaRepository sayesinde; save(), findAll(), findById(), deleteById() gibi
    // temel veritabanı komutlarının hepsi arka planda otomatik olarak hazırlandı!
// Sadece metod isimlerini yazıyoruz, Spring arka planda SQL sorgusunu kendi üretiyor!
    boolean existsByName(String name);
    List<EncodingPreset> findByFormat(Format format);

    List<EncodingPreset> findByVideoCodec(VideoCodec videoCodec);
}