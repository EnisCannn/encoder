package com.example.encoderservice.encoder;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EncodingResult {
    private boolean success;
    private String outputPath;
    private int exitCode;
    private String errorMessage;
}