package es.docklite.docklitebackend.auth.service;

import es.docklite.docklitebackend.auth.entity.RefreshToken;
import es.docklite.docklitebackend.auth.repository.RefreshTokenRepository;
import es.docklite.docklitebackend.common.exception.InvalidCredentialsException;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days in ms
    private long refreshExpirationMs;

    public RefreshToken create(User user) {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000L))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(token);
    }

    public User validateAndGetUser(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (token.isRevoked()) {
            throw new InvalidCredentialsException("Refresh token revoked");
        }
        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new InvalidCredentialsException("Refresh token expired");
        }

        return userRepository.findById(token.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidCredentialsException("User not available"));
    }

    @Transactional
    public void revoke(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }
}
