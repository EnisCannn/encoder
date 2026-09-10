package com.example.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token ayarlari. Secret gateway ile ayni olmali; ikisi de ayni HS256
 * anahtariyla imzalayip dogruluyor.
 */
@ConfigurationProperties(prefix = "encoder.jwt")
public record JwtProperties(String secret, long expirationSeconds, String issuer) {
}
