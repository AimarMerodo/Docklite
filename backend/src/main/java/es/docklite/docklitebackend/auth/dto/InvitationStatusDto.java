package es.docklite.docklitebackend.auth.dto;

import java.time.LocalDateTime;

public record InvitationStatusDto(
        int usesRemaining,
        LocalDateTime expiresAt
) {
}
