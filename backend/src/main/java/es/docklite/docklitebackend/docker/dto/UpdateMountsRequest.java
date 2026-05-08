package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body for {@code PUT /containers/{id}/mounts}: full replacement of the
 * container's volume-mount list. Send an empty list to remove all mounts.
 */
public record UpdateMountsRequest(
        @NotNull @Valid List<VolumeMount> mounts
) {
}
