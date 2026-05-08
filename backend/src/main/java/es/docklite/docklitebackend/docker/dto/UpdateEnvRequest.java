package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body for {@code PUT /containers/{id}/env}: full replacement of the
 * container's environment variable list. Send an empty list to wipe
 * every user-defined env var (the image's defaults still apply).
 */
public record UpdateEnvRequest(
        @NotNull @Valid List<EnvVar> env
) {
}
