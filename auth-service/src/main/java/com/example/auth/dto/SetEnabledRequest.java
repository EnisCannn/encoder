package com.example.auth.dto;

import jakarta.validation.constraints.NotNull;

/** enabled=false hesabi banlar (giris engellenir), true bani kaldirir. */
public record SetEnabledRequest(
        @NotNull(message = "enabled alani zorunlu")
        Boolean enabled
) {}
