package es.docklite.docklitebackend.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Uniform pagination envelope used by every paginated endpoint.
 * <p>
 * For DB-backed endpoints, build with {@link #from(Page)}.
 * For in-memory lists (e.g. Docker daemon results filtered by ownership),
 * use {@link #of(List, int, int)} which slices the collection client-side.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int safeSize = size < 1 ? 20 : size;
        int safePage = Math.max(page, 0);
        long total = all.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageResponse<>(all.subList(from, to), safePage, safeSize, total, totalPages);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
