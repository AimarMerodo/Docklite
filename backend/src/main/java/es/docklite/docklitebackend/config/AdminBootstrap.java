package es.docklite.docklitebackend.config;

import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUsername.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("Admin bootstrap skipped: ADMIN_USERNAME, ADMIN_EMAIL or ADMIN_PASSWORD not set");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin '{}' already exists, skipping bootstrap", adminEmail);
            return;
        }
        User admin = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("Bootstrap admin '{}' created", adminEmail);
    }
}
