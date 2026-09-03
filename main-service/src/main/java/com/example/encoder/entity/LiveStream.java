package com.example.encoder.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.UUID;

@Entity
@Data
public class LiveStream {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String streamName;    // Yayının Adı
    private String inputUrl;      // Kaynak URL
    private String outputFolder;  // HLS dosyalarının kaydedileceği klasör
    private String status;        // STARTING, LIVE, ENDED, ERROR
    private Long streamStartTime; // Yayının başladığı an (Epoch Milisaniye)
    private Long streamEndTime;   // YENİ: Yayının durdurulduğu an (Epoch Milisaniye)
}