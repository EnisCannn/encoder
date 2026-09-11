package com.example.auth.service;

import com.example.auth.dto.UserResponse;
import com.example.auth.entity.AppUser;
import com.example.auth.entity.Role;
import com.example.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Yonetici paneli: baskasinin parolasini sifirlama, banlama ve silme.
 * Rol kontrolu controller'da (X-Auth-Role); burada yalnizca "kendine ve son
 * yoneticiye yapamazsin" kurallari var, yoksa yonetici sistemi kilitleyebilir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(AuthService::toResponse).toList();
    }

    @Transactional
    public void setPassword(UUID userId, String newPassword, String actor) {
        AppUser user = find(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("{} kullanicisinin parolasi {} tarafindan sifirlandi", user.getUsername(), actor);
    }

    @Transactional
    public UserResponse setEnabled(UUID userId, boolean enabled, String actor) {
        AppUser user = find(userId);
        if (!enabled) {
            guardNotSelf(user, actor, "Kendi hesabinizi banlayamazsiniz");
            guardNotLastAdmin(user, "Son aktif yonetici banlanamaz");
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        log.info("{} kullanicisi {} tarafindan {}", user.getUsername(), actor,
                enabled ? "aktif edildi" : "banlandi");
        return AuthService.toResponse(user);
    }

    @Transactional
    public void delete(UUID userId, String actor) {
        AppUser user = find(userId);
        guardNotSelf(user, actor, "Kendi hesabinizi silemezsiniz");
        guardNotLastAdmin(user, "Son aktif yonetici silinemez");
        userRepository.delete(user);
        log.info("{} kullanicisi {} tarafindan silindi", user.getUsername(), actor);
    }

    private AppUser find(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanici bulunamadi: " + userId));
    }

    private static void guardNotSelf(AppUser target, String actor, String message) {
        if (target.getUsername().equals(actor)) {
            throw new ForbiddenException(message);
        }
    }

    /** Aktif tek yoneticiyi kaybedersek panele bir daha kimse giremez. */
    private void guardNotLastAdmin(AppUser target, String message) {
        if (target.getRole() != Role.ADMIN || !target.isEnabled()) return;
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ForbiddenException(message);
        }
    }
}
