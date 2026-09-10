package com.example.apigateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * secret auth-service ile ayni olmali. publicPaths, token istenmeyen uclar.
 */
@ConfigurationProperties(prefix = "encoder.jwt")
public record JwtProperties(String secret, List<String> publicPaths) {
}
