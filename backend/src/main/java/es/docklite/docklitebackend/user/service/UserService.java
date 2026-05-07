package es.docklite.docklitebackend.user.service;

import es.docklite.docklitebackend.auth.service.RefreshTokenService;
import es.docklite.docklitebackend.common.exception.InvalidCredentialsException;
import es.docklite.docklitebackend.user.dto.PasswordResetResponse;
import es.docklite.docklitebackend.user.dto.UpdateProfileRequest;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String PASSWORD_ALPHABET =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GENERATED_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public User updatePassword(User currentUser, UpdateProfileRequest req) {
        if (!passwordEncoder.matches(req.currentPassword(), currentUser.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        currentUser.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        return userRepository.save(currentUser);
    }

    /**
     * Generates a random temporary password, sets it for the target user
     * and returns it so the admin can communicate it manually.
     */
    public PasswordResetResponse resetPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String temporary = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        userRepository.save(user);

        return new PasswordResetResponse(user.getId(), user.getUsername(), temporary);
    }

    public void disable(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setEnabled(false);
        userRepository.save(user);
        // Revoke any active refresh tokens so the user is logged out across devices
        refreshTokenService.revokeAllForUser(userId);
    }

    public void enable(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setEnabled(true);
        userRepository.save(user);
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
