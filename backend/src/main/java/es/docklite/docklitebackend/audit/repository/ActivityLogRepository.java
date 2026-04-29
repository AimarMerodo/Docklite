package es.docklite.docklitebackend.audit.repository;

import es.docklite.docklitebackend.audit.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findByUserId(Long userId, Pageable pageable);
}
