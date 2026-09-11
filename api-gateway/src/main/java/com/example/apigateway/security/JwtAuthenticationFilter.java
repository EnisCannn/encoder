package com.example.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Her istegi gecerli bir JWT tasiyor mu diye kontrol eder. Basarili olursa
 * kullanici adi ve rolu asagidaki servislere baslik olarak gecirilir; boylece
 * main-service token cozmek zorunda kalmaz.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_HEADER = "X-Auth-User";
    public static final String ROLE_HEADER = "X-Auth-Role";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> publicPaths;
    private final SecretKey key;

    public JwtAuthenticationFilter(JwtProperties properties) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "encoder.jwt.secret en az 32 karakter olmali (HS256 anahtar boyutu)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.publicPaths = properties.publicPaths() == null ? List.of() : properties.publicPaths();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Preflight istegi Authorization basligi tasimaz; CORS katmani cevaplar.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Token bulunamadi");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(header.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();

            // Istemciden gelen sahte X-Auth-* basliklari ezilir: asagidaki
            // servisler bu basliklara guveniyor, disaridan gelmeleri kabul edilemez.
            ServerHttpRequest mutated = request.mutate()
                    .header(USER_HEADER, claims.getSubject())
                    .header(ROLE_HEADER, String.valueOf(claims.get("role")))
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Gecersiz token ({}): {}", path, ex.getMessage());
            return unauthorized(exchange, "Token gecersiz veya suresi dolmus");
        }
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Yonlendirme filtrelerinden once calissin
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
