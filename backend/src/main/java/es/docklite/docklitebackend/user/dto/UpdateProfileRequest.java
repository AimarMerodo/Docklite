package es.docklite.docklitebackend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6) String newPassword
) {
}
