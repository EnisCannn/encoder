package com.example.encoder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Bir encode ciktisinin VMAF kalite olcumu.
 *
 * Encode isinin kendisinden ayri bir tablo ve ayri bir kuyruk: olcum encode
 * akisina eklenirse is COMPLETED'a gec kalir, paketin SMIL'i gecikir ve
 * ilerleme cubugu yaniltir. Boylece encode hatti hic etkilenmiyor.
 */
@Entity
@Table(name = "quality_measurements")
@Getter
@Setter
@NoArgsConstructor
public class QualityMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Olculen encode isi. Is basina tek olcum tutuluyor, tekrar olcum ayni satiri gunceller. */
    @Column(nullable = false, unique = true)
    private UUID encodingJobId;

    @Enumerated(EnumType.STRING)
    private MeasurementStatus status = MeasurementStatus.PENDING;

    /** Ortalama VMAF (0-100). */
    private Double vmafScore;

    /** En dusuk kare skoru - ortalama iyi gorunse de kotu anlari yakalar. */
    private Double vmafMin;

    /** Harmonik ortalama - dusuk skorlari daha agir cezalandirir. */
    private Double vmafHarmonicMean;

    /** Olcumun kac saniyesi ornekledigi; null/0 ise dosyanin tamami olculdu. */
    private Integer sampledSeconds;

    /**
     * Ciktinin GERCEK kare hizi, olcum aninda ffprobe ile okunur.
     *
     * Sablondaki nominal deger yetmiyor: sablonda kare hizi bos birakilirsa
     * (onerilen kullanim) cikti kaynagin hizinda uretiliyor ama sablonda bir
     * sayi yazmiyor. Karsilastirma gruplarini nominal degere gore kurunca
     * "25 fps zorlanmis" ile "kaynak zaten 25 fps" ayri gruplara dusuyordu.
     */
    private Double outputFrameRate;

    @Column(length = 2000)
    private String errorMessage;

    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant completedAt;
}
