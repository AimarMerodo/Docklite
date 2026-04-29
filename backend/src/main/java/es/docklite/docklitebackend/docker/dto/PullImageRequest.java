package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.NotBlank;

public record PullImageRequest(
        @NotBlank String image,
        String tag
) {
    public String tagOrLatest() {
        return (tag == null || tag.isBlank()) ? "latest" : tag;
    }

    public String fullReference() {
        return image + ":" + tagOrLatest();
    }
}
