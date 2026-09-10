package com.example.auth.controller;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.dto.UserResponse;
import com.example.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Token'i gateway dogruladiktan sonra kullanici adini X-Auth-User basligiyla
     * iletir; bu uc onu okuyup profil bilgisini dondurur.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@RequestHeader("X-Auth-User") String username) {
        return ResponseEntity.ok(authService.findByUsername(username));
    }

    /**
     * Kullanici yalnizca kendi parolasini degistirebilir; hedef kullanici
     * govdeden degil gateway'in dogruladigi token'dan geliyor.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("X-Auth-User") String username,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(username, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> users() {
        return ResponseEntity.ok(authService.listUsers());
    }
}
