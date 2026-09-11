package com.example.auth.controller;

import com.example.auth.dto.AdminSetPasswordRequest;
import com.example.auth.dto.SetEnabledRequest;
import com.example.auth.dto.UserResponse;
import com.example.auth.service.AdminUserService;
import com.example.auth.service.ForbiddenException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Yalnizca ADMIN rolu icin. Rol, gateway'in token'dan cikarip ekledigi
 * X-Auth-Role basligindan okunuyor; istemcinin gonderdigi baslik gateway'de
 * eziliyor, o yuzden buna guvenilebilir.
 */
@RestController
@RequestMapping("/api/auth/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final String ROLE_HEADER = "X-Auth-Role";
    private static final String USER_HEADER = "X-Auth-User";

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(@RequestHeader(ROLE_HEADER) String role) {
        requireAdmin(role);
        return ResponseEntity.ok(adminUserService.listUsers());
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> setPassword(
            @RequestHeader(ROLE_HEADER) String role,
            @RequestHeader(USER_HEADER) String actor,
            @PathVariable UUID id,
            @Valid @RequestBody AdminSetPasswordRequest request) {
        requireAdmin(role);
        adminUserService.setPassword(id, request.newPassword(), actor);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<UserResponse> setEnabled(
            @RequestHeader(ROLE_HEADER) String role,
            @RequestHeader(USER_HEADER) String actor,
            @PathVariable UUID id,
            @Valid @RequestBody SetEnabledRequest request) {
        requireAdmin(role);
        return ResponseEntity.ok(adminUserService.setEnabled(id, request.enabled(), actor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(ROLE_HEADER) String role,
            @RequestHeader(USER_HEADER) String actor,
            @PathVariable UUID id) {
        requireAdmin(role);
        adminUserService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }

    private static void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("Bu islem icin yonetici yetkisi gerekli");
        }
    }
}
