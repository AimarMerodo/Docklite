package es.docklite.docklitebackend.user.dto;

public record PasswordResetResponse(
        Long userId,
        String username,
        String temporaryPassword
) {
}
