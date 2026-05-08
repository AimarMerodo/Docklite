package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body for {@code POST /containers/{id}/rename}. Live operation, no
 * recreate. Docker's name rules apply: alphanumeric plus dot, underscore
 * and hyphen, starting with an alphanumeric.
 */
public record RenameContainerRequest(
        @NotBlank
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$",
                message = "name may only contain letters, digits, dot, underscore and hyphen"
        )
        String name
) {
}
