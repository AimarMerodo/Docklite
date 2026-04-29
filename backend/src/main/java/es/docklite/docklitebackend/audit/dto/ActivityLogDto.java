package es.docklite.docklitebackend.audit.dto;

import es.docklite.docklitebackend.audit.entity.ActivityLog;

import java.time.LocalDateTime;

public record ActivityLogDto(
        Long id,
        Long userId,
        String resourceId,
        String resourceType,
        String action,
        LocalDateTime createdAt
) {
    public static ActivityLogDto from(ActivityLog log) {
        return new ActivityLogDto(
                log.getId(),
                log.getUserId(),
                log.getResourceId(),
                log.getResourceType() != null ? log.getResourceType().name() : null,
                log.getAction().name(),
                log.getCreatedAt()
        );
    }
}
