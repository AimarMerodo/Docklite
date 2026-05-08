package es.docklite.docklitebackend.docker.dto;

import java.util.List;

/**
 * Minimal info read from a registry without pulling the image layers.
 * Used by {@code GET /images/inspect-remote} to feed the create-container
 * wizard's port autocomplete with images the user has not pulled yet.
 */
public record RemoteImageInfoDto(
        String ref,
        List<ImageDto.ExposedPortInfo> exposedPorts
) {
}
