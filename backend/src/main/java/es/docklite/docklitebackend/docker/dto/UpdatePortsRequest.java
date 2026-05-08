package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body for {@code PUT /containers/{id}/ports}: full replacement of the
 * container's published port-binding list. Send an empty list to remove
 * every published port mapping (the container will keep any
 * Dockerfile-EXPOSE entries but won't be reachable from the host).
 */
public record UpdatePortsRequest(
        @NotNull @Valid List<PortMapping> ports
) {
}
