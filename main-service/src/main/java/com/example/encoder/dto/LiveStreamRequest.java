package com.example.encoder.dto;

import lombok.Data;

@Data
public class LiveStreamRequest {
    private String streamName;
    private String inputUrl;

    private String subtitlePath;
    private String dubbingPath;
}