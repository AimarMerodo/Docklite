package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.NotBlank;

public record EnvVar(
        @NotBlank String name,
        String value
) {
}
