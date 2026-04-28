package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateContainerRequest(
        @NotBlank String image,
        String name,
        boolean autoStart
) {
}
