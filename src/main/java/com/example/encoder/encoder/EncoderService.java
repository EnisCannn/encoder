package com.example.encoder.encoder;

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.Video;
import java.nio.file.Path;

public interface EncoderService {
    EncodingResult encode(Video video, EncodingPreset preset, Path output);
}