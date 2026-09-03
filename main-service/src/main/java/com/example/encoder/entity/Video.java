package com.example.encoder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String originalFileName;
    private String storedFileName;
    private String path;
    private Long size;
    private BigDecimal duration;
    private Integer width;
    private Integer height;
    private String videoCodec;
    private String audioCodec;
    private Instant createdAt = Instant.now();
}