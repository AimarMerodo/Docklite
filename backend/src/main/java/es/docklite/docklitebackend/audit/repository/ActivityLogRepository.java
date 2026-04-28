package es.docklite.docklitebackend.audit.repository;

import es.docklite.docklitebackend.audit.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
}
