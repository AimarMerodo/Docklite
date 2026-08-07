package es.docklite.docklitebackend.auth.service;


import es.docklite.docklitebackend.auth.dto.AuthResponse;
import es.docklite.docklitebackend.auth.dto.LoginRequest;
import es.docklite.docklitebackend.auth.entity.RefreshToken;
import es.docklite.docklitebackend.auth.jwt.JwtProvider;
import es.docklite.docklitebackend.common.exception.AccountDisabledException;
import es.docklite.docklitebackend.common.exception.AccountLockedException;
import es.docklite.docklitebackend.common.exception.InvalidCredentialsException;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginAttemptService loginAttempts;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.expiration}")
    private long accessExpirationMs;

    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${app.demo.email:demo@docklite.local}")
    private String demoEmail;

    public AuthResponse login(LoginRequest req) {
        if (loginAttempts.isLocked(req.email())) {
            throw new AccountLockedException("Too many failed attempts. Try again in 15 minutes.");
        }

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> {
                    loginAttempts.recordFailure(req.email());
                    return new InvalidCredentialsException("Invalid credentials");
                });

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginAttempts.recordFailure(req.email());
            throw new InvalidCredentialsException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new AccountDisabledException("Account is disabled");
        }

        loginAttempts.recordSuccess(req.email());

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshTokenValue) {
        User user = refreshTokenService.validateAndGetUser(refreshTokenValue);
        // Rotation: invalidate the old refresh token and issue a fresh pair.
        refreshTokenService.revoke(refreshTokenValue);
        return buildAuthResponse(user);
    }

    public void logout(String refreshTokenValue) {
        refreshTokenService.revoke(refreshTokenValue);
    }

    public boolean isDemoEnabled() {
        return demoEnabled;
    }

    /**
     * Passwordless login as the demo user. Only available when demo mode is on;
     * otherwise the endpoint behaves as if it didn't exist (404). The demo
     * account is read-only (ROLE_DEMO), so handing out its session is safe.
     */
    public AuthResponse demoLogin() {
        if (!demoEnabled) {
            throw new EntityNotFoundException("Demo mode is not enabled");
        }
        User demo = userRepository.findByEmail(demoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Demo user not found"));
        return buildAuthResponse(demo);
    }

    public AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtProvider.generateToken(user);
        RefreshToken refresh = refreshTokenService.create(user);
        return new AuthResponse(
                accessToken,
                refresh.getToken(),
                accessExpirationMs / 1000L,
                user.getUsername(),
                user.getRole().name()
        );
    }
}
