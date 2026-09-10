package com.example.auth.service;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.dto.UserResponse;
import com.example.auth.entity.AppUser;
import com.example.auth.entity.Role;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Bu kullanici adi zaten alinmis: " + username);
        }

        AppUser user = AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);
        log.info("Yeni kullanici kaydedildi: {}", username);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Kullanici yok ve parola yanlis ayni mesaji dondurur: hangi kullanici
        // adlarinin kayitli oldugu disaridan anlasilmasin.
        AppUser user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("Kullanici adi veya parola hatali"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Bu hesap devre disi birakilmis");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Kullanici adi veya parola hatali");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole().name(),
                jwtService.expirationSeconds());
    }

    /**
     * Parola degisikligi. Kullanici adi gateway'in ekledigi X-Auth-User
     * basligindan geliyor; yani kimse baskasinin parolasini degistiremez.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Kullanici bulunamadi: " + username));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // 401 degil 400: kullanici zaten kimligi dogrulanmis durumda,
            // bu bir dogrulama hatasi. 401 donseydi arayuzdeki interceptor
            // bunu "oturum dustu" sanip kullaniciyi cikisa goturecekti.
            throw new IllegalArgumentException("Mevcut parola hatali");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Yeni parola eskisiyle ayni olamaz");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("{} parolasini degistirdi", username);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(AuthService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(AuthService::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Kullanici bulunamadi: " + username));
    }

    private static UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt());
    }

    /** Girisin basarisiz oldugu her durum; disariya 401 olarak cikar. */
    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String message) {
            super(message);
        }
    }
}
