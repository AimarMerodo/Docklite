package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.command.InspectVolumeResponse;

import java.util.List;

public record VolumeDto(
        String name,
        String driver,
        String mountpoint,
        /** Names of containers (running or stopped) currently mounting
         *  this volume. Empty if no container references it. */
        List<String> usedBy
) {
    public static VolumeDto from(InspectVolumeResponse v) {
        return from(v, List.of());
    }

    public static VolumeDto from(InspectVolumeResponse v, List<String> usedBy) {
        return new VolumeDto(
                v.getName(),
                v.getDriver(),
                v.getMountpoint(),
                usedBy != null ? usedBy : List.of()
        );
    }
}
