package es.docklite.docklitebackend.auth.dto;

import es.docklite.docklitebackend.auth.entity.Invitation;

import java.time.LocalDateTime;

public record InvitationDto(
        Long id,
        String token,
        String url,
        int maxUses,
        int usesRemaining,
        LocalDateTime expiresAt,
        boolean cancelled,
        boolean expired,
        boolean exhausted,
        boolean active,
        Long createdBy,
        LocalDateTime createdAt
) {
    /** Copy without the secret token/URL — what the read-only demo role sees. */
    public InvitationDto masked() {
        return new InvitationDto(id, null, null, maxUses, usesRemaining, expiresAt,
                cancelled, expired, exhausted, active, createdBy, createdAt);
    }

    public static InvitationDto from(Invitation inv, String publicUrl) {
        boolean expired = LocalDateTime.now().isAfter(inv.getExpiresAt());
        boolean exhausted = inv.getUsesRemaining() <= 0;
        boolean active = !inv.isCancelled() && !expired && !exhausted;
        return new InvitationDto(
                inv.getId(),
                inv.getToken(),
                publicUrl + "/invite/" + inv.getToken(),
                inv.getMaxUses(),
                inv.getUsesRemaining(),
                inv.getExpiresAt(),
                inv.isCancelled(),
                expired,
                exhausted,
                active,
                inv.getCreatedBy(),
                inv.getCreatedAt()
        );
    }
}
