package com.example.encoder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Birden fazla EncodingPreset'i tek bir "paket" altında toplar.
 * Kullanici bir video yukleyip set sectiginde, setteki her preset icin ayri bir job acilir.
 */
@Entity
@Table(name = "encode_sets")
@Getter
@Setter
@NoArgsConstructor
public class EncodeSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Setteki presetler. Sira onemli: HLS master playlist ve SMIL dosyasinda
     * varyantlar bu sirayla yazilacak (genelde yuksek kaliteden dusuge).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "encode_set_presets",
            joinColumns = @JoinColumn(name = "encode_set_id"),
            inverseJoinColumns = @JoinColumn(name = "preset_id")
    )
    @OrderColumn(name = "sort_order")
    private List<EncodingPreset> presets = new ArrayList<>();

    private Boolean isActive = true;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}
