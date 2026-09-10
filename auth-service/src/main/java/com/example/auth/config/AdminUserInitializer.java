package com.example.auth.config;

import com.example.auth.entity.AppUser;
import com.example.auth.entity.Role;
import com.example.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ilk acilista bir yonetici hesabi olusturur; aksi halde sisteme girecek
 * kimse olmaz. Var olan hesabin parolasina dokunmaz.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "admin123";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${encoder.admin.username:admin}")
    private String adminUsername;

    @Value("${encoder.admin.password:" + DEFAULT_PASSWORD + "}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Yonetici hesabi zaten var: {}", adminUsername);
            return;
        }

        userRepository.save(AppUser.builder()
                .username(adminUsername)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build());

        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            log.warn("Yonetici hesabi varsayilan parolayla olusturuldu ({}/{}). "
                    + "ENCODER_ADMIN_PASSWORD ile degistirin.", adminUsername, DEFAULT_PASSWORD);
        } else {
            log.info("Yonetici hesabi olusturuldu: {}", adminUsername);
        }
    }
}
