package com.example.encoder.entity;

import com.example.encoder.entity.enums.Format;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.example.encoder.entity.enums.AudioCodec;
import com.example.encoder.entity.enums.Container;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity // Bu sınıfın bir veritabanı tablosu olduğunu belirtir
@Table(name = "encoding_presets") // Veritabanındaki tablonun adını belirler
@Getter // Lombok: Tüm alanlar için otomatik getter metodları üretir
@Setter // Lombok: Tüm alanlar için otomatik setter metodları üretir
public class EncodingPreset {

    @Id // Bu alanın Primary Key (Birincil Anahtar) olduğunu belirtir
    @GeneratedValue(strategy = GenerationType.UUID) // UUID'nin otomatik üretilmesini sağlar
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private Format format;

    @Enumerated(EnumType.STRING)
    private VideoCodec videoCodec;

    @Enumerated(EnumType.STRING)
    private AudioCodec audioCodec;

    @Enumerated(EnumType.STRING)
    private Container container;

    private Integer width;
    private Integer height;
    private Integer videoBitrate;
    private Integer audioBitrate;
    private BigDecimal frameRate;

    private String presetProfile;
    private Integer crf;
    private Integer audioSampleRate;
    private Integer audioChannels;

    /**
     * Kalibrasyon taramasi icin uretilmis gecici sablon. Sablonlar listesinde
     * gorunmez; amaci tek bir olcum noktasi uretmek, yeniden kullanilmak degil.
     */
    private Boolean calibration = false;

    private Boolean isActive = true;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}