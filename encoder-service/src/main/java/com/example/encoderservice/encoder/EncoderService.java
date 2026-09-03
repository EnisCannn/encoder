    package com.example.encoderservice.encoder;

    import com.example.encoderservice.entity.EncodingJob;
    import java.nio.file.Path;

    public interface EncoderService {
        // Sadece Video ve Preset yerine tüm Job nesnesini alıyoruz
        EncodingResult encode(EncodingJob job, Path output);
    }