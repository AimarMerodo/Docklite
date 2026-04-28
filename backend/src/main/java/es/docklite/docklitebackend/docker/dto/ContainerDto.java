package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;

public record ContainerDto(
        String id,
        String name,
        String image,
        String state,
        String status
) {
    public static ContainerDto from(Container c) {
        String name = (c.getNames() != null && c.getNames().length > 0)
                ? c.getNames()[0].replaceFirst("^/", "")
                : null;
        return new ContainerDto(
                c.getId(),
                name,
                c.getImage(),
                c.getState(),
                c.getStatus()
        );
    }

    public static ContainerDto from(InspectContainerResponse r) {
        String name = r.getName() != null ? r.getName().replaceFirst("^/", "") : null;
        return new ContainerDto(
                r.getId(),
                name,
                r.getConfig().getImage(),
                r.getState().getStatus(),
                null
        );
    }
}
