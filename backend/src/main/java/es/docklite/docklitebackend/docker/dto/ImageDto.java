package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.Image;

import java.util.Arrays;
import java.util.List;

public record ImageDto(
        String id,
        List<String> tags,
        Long size,
        String created
) {
    public static ImageDto from(Image img) {
        List<String> tags = img.getRepoTags() != null
                ? Arrays.asList(img.getRepoTags())
                : List.of();
        return new ImageDto(img.getId(), tags, img.getSize(), String.valueOf(img.getCreated()));
    }

    public static ImageDto from(InspectImageResponse img) {
        return new ImageDto(
                img.getId(),
                img.getRepoTags() != null ? img.getRepoTags() : List.of(),
                img.getSize(),
                img.getCreated()
        );
    }
}
