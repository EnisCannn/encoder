package com.example.encoder.dto;

import lombok.Data;

@Data
public class CutRequest {
    private String videoId;
    private String startTime;
    private String endTime;
}