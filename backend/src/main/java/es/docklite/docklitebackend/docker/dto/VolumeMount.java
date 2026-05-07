package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.NotBlank;

public record VolumeMount(
        @NotBlank String volumeName,
        @NotBlank String containerPath,
        boolean readOnly
) {
}
