package es.docklite.docklitebackend.docker.dto;

import java.util.List;

/**
 * Body for {@code PUT /containers/{id}/command}: replaces entrypoint
 * and/or command. A null or empty array on a field means "revert to the
 * image's default" for that field.
 */
public record UpdateCommandRequest(
        List<String> entrypoint,
        List<String> command
) {
}
