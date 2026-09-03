package com.example.encoder.dto;

import lombok.Data;

@Data
public class EncodeMediaRequest {
    private String videoId;
    private String presetId;
    private String subtitlePath; // İsteğe bağlı
    private String dubbingPath;  // İsteğe bağlı
}