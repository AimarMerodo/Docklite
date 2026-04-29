package es.docklite.docklitebackend.auth.service;


import es.docklite.docklitebackend.auth.dto.AuthResponse;
import es.docklite.docklitebackend.auth.dto.LoginRequest;
import es.docklite.docklitebackend.auth.jwt.JwtProvider;
import es.docklite.docklitebackend.common.exception.InvalidCredentialsException;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String token = jwtProvider.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
