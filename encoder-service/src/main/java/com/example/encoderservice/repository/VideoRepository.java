package com.example.encoderservice.repository;

import com.example.encoderservice.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {
}