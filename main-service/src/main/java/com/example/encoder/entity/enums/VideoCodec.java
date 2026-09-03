package com.example.encoder.entity.enums;

public enum VideoCodec {
    H264("libx264"),
    H265("libx265"),
    AV1("libsvtav1");

    private final String ffmpegFlag;

    VideoCodec(String ffmpegFlag) {
        this.ffmpegFlag = ffmpegFlag;
    }

    public String getFfmpegFlag() {
        return ffmpegFlag;
    }
}