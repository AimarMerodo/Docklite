package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.command.InspectVolumeResponse;

public record VolumeDto(
        String name,
        String driver,
        String mountpoint
) {
    public static VolumeDto from(InspectVolumeResponse v) {
        return new VolumeDto(
                v.getName(),
                v.getDriver(),
                v.getMountpoint()
        );
    }
}
