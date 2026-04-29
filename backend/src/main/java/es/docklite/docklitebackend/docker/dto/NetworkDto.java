package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.model.Network;

public record NetworkDto(
        String id,
        String name,
        String driver,
        String scope
) {
    public static NetworkDto from(Network n) {
        return new NetworkDto(
                n.getId(),
                n.getName(),
                n.getDriver(),
                n.getScope()
        );
    }
}
