package com.example.encoder.service;

import com.example.encoder.entity.Video;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface VideoService {
    // Dışarıdan sadece bu metodun varlığı bilinir, içi görünmez
    Video uploadVideo(MultipartFile file) throws IOException;
}