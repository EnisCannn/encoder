package com.example.encoder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateEncodeSetRequest(

        @NotBlank(message = "Paket adi bos olamaz")
        @Size(max = 100, message = "Paket adi en fazla 100 karakter olabilir")
        String name,

        @Size(max = 500, message = "Aciklama en fazla 500 karakter olabilir")
        String description,

        @NotEmpty(message = "Pakette en az bir preset secilmeli")
        List<UUID> presetIds
) {}
