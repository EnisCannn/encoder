package com.example.encoderservice.entity;

import com.example.encoderservice.entity.enums.SubtitleMode;
import com.example.encoderservice.entity.EncodingPreset;
import com.example.encoderservice.entity.JobStatus;
import com.example.encoderservice.entity.Video;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "encoding_jobs")
@Getter
@Setter

@NoArgsConstructor
public class EncodingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String inputFileName;
    private String inputPath;
    private String outputFileName;
    private String outputPath;
    // EncodingJob.java içerisine eklenecek alanlar
    private String subtitlePath; // Yüklenen kaynak dosya. Örn: "C:/video_test/assets/sub_xxx.srt"
    private String dubbingPath;  // Örn: "C:/video_test/dublaj.mp3"

    @Enumerated(EnumType.STRING)
    private SubtitleMode subtitleMode = SubtitleMode.NONE;

    // Çıktı klasörüne göre göreli .vtt yolu (SIDECAR modunda main-service doldurur)
    private String subtitleVttFileName;

    private String subtitleLanguage;
    private String subtitleLabel;


    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    private Integer progress = 0;

    @Column(length = 2000)
    private String errorMessage;

    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt = Instant.now();

    // Aynı EncodeSet'ten doğan işleri gruplayan kimlik (tekli işlerde null)
    private UUID batchId;

    // İşin hangi paketten doğduğu (tekli işlerde null)
    private UUID encodeSetId;

    /**
     * Son VMAF olcumunun ortalamasi. Asil kayit quality_measurements tablosunda;
     * bu alan arayuz listeyi cekerken join yapmasin diye kopyalaniyor.
     */
    private Double vmafScore;

    @ManyToOne
    @JoinColumn(name = "preset_id")
    private EncodingPreset preset;

    @ManyToOne
    @JoinColumn(name = "video_id")
    private Video video;
}