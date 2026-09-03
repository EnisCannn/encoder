package com.example.encoder.dto;
import lombok.Data;

@Data
public class LiveClipRequest {
    private String streamId;
    private Long startTime; // YENİ: Epoch timestamp (Milisaniye bazında)
    private Long endTime;   // YENİ: Epoch timestamp (Milisaniye bazında)
    private String presetId;
}