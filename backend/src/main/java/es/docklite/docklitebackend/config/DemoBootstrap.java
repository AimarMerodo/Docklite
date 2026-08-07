package es.docklite.docklitebackend.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import es.docklite.docklitebackend.docker.dto.CreateContainerRequest;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.docker.service.ContainerService;
import es.docklite.docklitebackend.docker.service.ResourceOwnershipService;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Demo mode (DEMO_MODE=true): guarantees a read-only demo user and a couple of
 * lightweight seed containers it owns, so visitors see real lists, detail,
 * logs and stats. Idempotent — safe to run on every startup and periodically
 * (self-healing if someone stops or removes the seeds). The bean does not even
 * exist unless demo mode is enabled, so normal installs are unaffected.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoBootstrap implements ApplicationRunner {

    private record SeedSpec(String name, String image) {
    }

    // No published ports: avoids host port collisions and keeps the seeds unreachable.
    private static final List<SeedSpec> SEED_CONTAINERS = List.of(
            new SeedSpec("docklite-demo-web", "nginx:alpine"),
            new SeedSpec("docklite-demo-cache", "redis:alpine")
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContainerService containerService;
    private final ResourceOwnershipService ownershipService;
    private final DockerClient dockerClient;

    @Value("${app.demo.username:demo}")
    private String demoUsername;

    @Value("${app.demo.email:demo@docklite.local}")
    private String demoEmail;

    @Value("${app.demo.password:}")
    private String demoPassword;

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /**
     * Re-runs periodically: retries if Docker wasn't ready at startup and
     * restores the seeds if an admin stopped or removed them.
     */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT6H")
    public void seed() {
        try {
            User demoUser = ensureDemoUser();
            for (SeedSpec spec : SEED_CONTAINERS) {
                ensureSeedContainer(spec, demoUser);
            }
            log.info("Demo seed OK: user '{}' with {} seed containers", demoEmail, SEED_CONTAINERS.size());
        } catch (Exception e) {
            // An ApplicationRunner that throws would abort startup, and Docker
            // may simply not be ready yet — log and let the schedule retry.
            log.warn("Demo seed failed (will retry on next scheduled run): {}", e.getMessage());
        }
    }

    private User ensureDemoUser() {
        return userRepository.findByEmail(demoEmail).orElseGet(() -> {
            // Nobody needs to know this password: visitors enter through
            // POST /api/v1/auth/demo and the account is read-only anyway.
            String password = demoPassword.isBlank() ? UUID.randomUUID().toString() : demoPassword;
            User demo = User.builder()
                    .username(demoUsername)
                    .email(demoEmail)
                    .passwordHash(passwordEncoder.encode(password))
                    .role(Role.DEMO)
                    .build();
            User saved = userRepository.save(demo);
            log.info("Demo user '{}' created", demoEmail);
            return saved;
        });
    }

    private void ensureSeedContainer(SeedSpec spec, User demoUser) {
        Container existing = findByName(spec.name());
        if (existing == null) {
            // ContainerService.create pulls the image if missing, registers
            // ownership and writes the activity log — the demo looks alive.
            containerService.create(new CreateContainerRequest(
                    spec.image(), spec.name(), true,
                    null, null, null, null,
                    "unless-stopped", null, null
            ), demoUser);
            log.info("Seed container '{}' created", spec.name());
            return;
        }
        if (!"running".equalsIgnoreCase(existing.getState())) {
            startReRegisteringIfNeeded(existing, spec, demoUser);
        }
    }

    private void startReRegisteringIfNeeded(Container container, SeedSpec spec, User demoUser) {
        try {
            containerService.start(container.getId(), demoUser);
        } catch (AccessDeniedException e) {
            // Ownership row lost (e.g. the database was recreated but the
            // container survived): re-register and retry once.
            ownershipService.register(container.getId(), ResourceType.CONTAINER, spec.name(), demoUser.getId());
            containerService.start(container.getId(), demoUser);
        }
        log.info("Seed container '{}' restarted", spec.name());
    }

    private Container findByName(String name) {
        // Docker's name filter matches substrings, so compare exactly against
        // the canonical "/name" form.
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(name))
                .exec().stream()
                .filter(c -> Arrays.asList(c.getNames()).contains("/" + name))
                .findFirst()
                .orElse(null);
    }
}
