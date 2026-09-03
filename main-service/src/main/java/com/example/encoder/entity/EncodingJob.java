package com.example.encoder.entity;

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
    private String subtitlePath; // Örn: "C:/video_test/altyazi.srt"
    private String dubbingPath;  // Örn: "C:/video_test/dublaj.mp3"


    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    private Integer progress = 0;

    @Column(length = 2000)
    private String errorMessage;

    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "preset_id")
    private EncodingPreset preset;

    @ManyToOne
    @JoinColumn(name = "video_id")
    private Video video;
}