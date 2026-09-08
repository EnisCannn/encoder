package com.example.encoder.service;

import com.example.encoder.entity.Video;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.List;

public interface VideoService {

    List<Video> getAllVideos();
    // Dışarıdan sadece bu metodun varlığı bilinir, içi görünmez
    Video uploadVideo(MultipartFile file) throws IOException;
}