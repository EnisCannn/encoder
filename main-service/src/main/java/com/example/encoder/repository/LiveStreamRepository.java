package com.example.encoder.repository;

import com.example.encoder.entity.LiveStream;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LiveStreamRepository extends JpaRepository<LiveStream, UUID> {
}