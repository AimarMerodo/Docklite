package es.docklite.docklitebackend.docker.dto;

/**
 * Single host port currently bound by a container on the daemon.
 * Returned by {@code GET /containers/ports-in-use} so the frontend can
 * validate inline whether a host port is free before publishing one.
 * Deliberately omits container id and owner info — only the name is
 * exposed, just enough for the user to recognise the conflict.
 */
public record PortInUseDto(
        int hostPort,
        String protocol,
        String containerName
) {
}
