package es.docklite.docklitebackend.audit.service;

import es.docklite.docklitebackend.audit.entity.ActivityAction;
import es.docklite.docklitebackend.audit.entity.ActivityLog;
import es.docklite.docklitebackend.audit.repository.ActivityLogRepository;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository repository;

    public void log(Long userId, String resourceId, ResourceType type, ActivityAction action) {
        ActivityLog entry = ActivityLog.builder()
                .userId(userId)
                .resourceId(resourceId)
                .resourceType(type)
                .action(action)
                .build();
        repository.save(entry);
    }
}
