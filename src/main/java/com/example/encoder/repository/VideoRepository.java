package com.example.encoder.repository;

import com.example.encoder.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {
}