package com.example.encoder.encoder;

import com.example.encoder.entity.EncodingJob;
import java.nio.file.Path;

public interface EncoderService {
    // Sadece Video ve Preset yerine tüm Job nesnesini alıyoruz
    EncodingResult encode(EncodingJob job, Path output);
}