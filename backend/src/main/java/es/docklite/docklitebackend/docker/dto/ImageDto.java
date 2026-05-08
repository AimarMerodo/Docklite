package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ImageDto(
        String id,
        List<String> tags,
        Long size,
        String created,
        List<ExposedPortInfo> exposedPorts,
        /** Usernames of users who have pulled this image. Visibility is
         *  decided in the service layer (admin sees all; regular users only
         *  see themselves). */
        List<String> owners,
        /** Names of containers (running or stopped) that currently
         *  reference this image. Empty if no container uses it. */
        List<String> usedBy
) {

    /** Single Dockerfile EXPOSE entry. */
    public record ExposedPortInfo(int port, String protocol) {}

    /** Listing path: the daemon's image list does not include config detail,
     *  so {@code exposedPorts} is left empty. */
    public static ImageDto from(Image img) {
        return from(img, List.of(), List.of());
    }

    public static ImageDto from(Image img, List<String> owners, List<String> usedBy) {
        List<String> tags = img.getRepoTags() != null
                ? Arrays.asList(img.getRepoTags())
                : List.of();
        return new ImageDto(
                img.getId(),
                tags,
                img.getSize(),
                String.valueOf(img.getCreated()),
                List.of(),
                owners != null ? owners : List.of(),
                usedBy != null ? usedBy : List.of());
    }

    /** Inspect path: extracts {@code Config.ExposedPorts} from the image. */
    public static ImageDto from(InspectImageResponse img) {
        return from(img, List.of(), List.of());
    }

    public static ImageDto from(InspectImageResponse img, List<String> owners, List<String> usedBy) {
        return new ImageDto(
                img.getId(),
                img.getRepoTags() != null ? img.getRepoTags() : List.of(),
                img.getSize(),
                img.getCreated(),
                buildExposedPorts(img),
                owners != null ? owners : List.of(),
                usedBy != null ? usedBy : List.of()
        );
    }

    private static List<ExposedPortInfo> buildExposedPorts(InspectImageResponse img) {
        if (img.getConfig() == null || img.getConfig().getExposedPorts() == null) {
            return List.of();
        }
        List<ExposedPortInfo> out = new ArrayList<>();
        for (ExposedPort ep : img.getConfig().getExposedPorts()) {
            if (ep == null) continue;
            String proto = ep.getProtocol() != null ? ep.getProtocol().toString() : "tcp";
            out.add(new ExposedPortInfo(ep.getPort(), proto));
        }
        return out;
    }
}
