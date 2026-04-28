package es.docklite.docklitebackend.auth.dto;

public record AuthResponse(
        String token,
        String username,
        String role
) {
}
