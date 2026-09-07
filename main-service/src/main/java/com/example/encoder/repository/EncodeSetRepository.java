package com.example.encoder.repository;

import com.example.encoder.entity.EncodeSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncodeSetRepository extends JpaRepository<EncodeSet, UUID> {

    boolean existsByName(String name);

    // Liste ekraninda her set icin ayri preset sorgusu atilmasin diye tek seferde cekiyoruz
    @Query("SELECT DISTINCT s FROM EncodeSet s LEFT JOIN FETCH s.presets ORDER BY s.createdAt DESC")
    List<EncodeSet> findAllWithPresets();

    @Query("SELECT s FROM EncodeSet s LEFT JOIN FETCH s.presets WHERE s.id = :id")
    Optional<EncodeSet> findByIdWithPresets(@Param("id") UUID id);
}
