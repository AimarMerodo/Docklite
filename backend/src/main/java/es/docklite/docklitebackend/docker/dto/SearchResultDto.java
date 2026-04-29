package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.model.SearchItem;

public record SearchResultDto(
        String name,
        String description,
        int stars,
        boolean official
) {
    public static SearchResultDto from(SearchItem item) {
        return new SearchResultDto(
                item.getName(),
                item.getDescription(),
                item.getStarCount() != null ? item.getStarCount() : 0,
                Boolean.TRUE.equals(item.isOfficial())
        );
    }
}
