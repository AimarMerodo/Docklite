package es.docklite.docklitebackend.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String username,
        String role
) {
}
