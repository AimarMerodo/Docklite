package es.docklite.docklitebackend.auth.service;

import es.docklite.docklitebackend.auth.dto.AcceptInvitationRequest;
import es.docklite.docklitebackend.auth.dto.AuthResponse;
import es.docklite.docklitebackend.auth.dto.CreateInvitationRequest;
import es.docklite.docklitebackend.auth.dto.InvitationDto;
import es.docklite.docklitebackend.auth.dto.InvitationStatusDto;
import es.docklite.docklitebackend.auth.entity.Invitation;
import es.docklite.docklitebackend.auth.repository.InvitationRepository;
import es.docklite.docklitebackend.common.exception.EmailAlreadyExistsException;
import es.docklite.docklitebackend.common.exception.InvitationNotFoundException;
import es.docklite.docklitebackend.common.exception.UsernameAlreadyExistsException;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Value("${app.public-url}")
    private String publicUrl;

    @Value("${app.invitation.default-max-uses:1}")
    private int defaultMaxUses;

    @Value("${app.invitation.default-expires-in-days:7}")
    private int defaultExpiresInDays;

    public InvitationDto create(CreateInvitationRequest req, User admin) {
        int maxUses = req.maxUses() != null ? req.maxUses() : defaultMaxUses;
        int expiresInDays = req.expiresInDays() != null ? req.expiresInDays() : defaultExpiresInDays;

        Invitation inv = Invitation.builder()
                .token(UUID.randomUUID().toString())
                .maxUses(maxUses)
                .usesRemaining(maxUses)
                .expiresAt(LocalDateTime.now().plusDays(expiresInDays))
                .cancelled(false)
                .createdBy(admin.getId())
                .build();
        inv = invitationRepository.save(inv);
        return InvitationDto.from(inv, publicUrl);
    }

    public Page<InvitationDto> listAll(Pageable pageable) {
        return invitationRepository.findAll(pageable)
                .map(inv -> InvitationDto.from(inv, publicUrl));
    }

    public void cancel(Long id) {
        Invitation inv = invitationRepository.findById(id)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));
        inv.setCancelled(true);
        invitationRepository.save(inv);
    }

    public InvitationStatusDto validate(String token) {
        Invitation inv = findActive(token);
        return new InvitationStatusDto(inv.getUsesRemaining(), inv.getExpiresAt());
    }

    @Transactional
    public AuthResponse accept(String token, AcceptInvitationRequest req) {
        Invitation inv = findActive(token);

        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        if (userRepository.existsByUsername(req.username())) {
            throw new UsernameAlreadyExistsException("Username already exists");
        }

        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.USER)
                .build();
        user = userRepository.save(user);

        inv.setUsesRemaining(inv.getUsesRemaining() - 1);
        invitationRepository.save(inv);

        return authService.buildAuthResponse(user);
    }

    private Invitation findActive(String token) {
        Invitation inv = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));
        if (inv.isCancelled()) {
            throw new InvitationNotFoundException("Invitation cancelled");
        }
        if (LocalDateTime.now().isAfter(inv.getExpiresAt())) {
            throw new InvitationNotFoundException("Invitation expired");
        }
        if (inv.getUsesRemaining() <= 0) {
            throw new InvitationNotFoundException("Invitation exhausted");
        }
        return inv;
    }
}
