package es.docklite.docklitebackend.auth.dto;

import jakarta.validation.constraints.Min;

public record CreateInvitationRequest(
        @Min(1) Integer maxUses,
        @Min(1) Integer expiresInDays
) {
}
