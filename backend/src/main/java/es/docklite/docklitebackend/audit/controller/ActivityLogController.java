package es.docklite.docklitebackend.audit.controller;

import es.docklite.docklitebackend.audit.dto.ActivityLogDto;
import es.docklite.docklitebackend.audit.entity.ActivityLog;
import es.docklite.docklitebackend.audit.repository.ActivityLogRepository;
import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogRepository repository;

    @GetMapping
    public PageResponse<ActivityLogDto> list(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        User user = (User) auth.getPrincipal();
        // DEMO shares the admin-wide view (read-only demo of the full screen).
        Page<ActivityLog> page = (user.getRole() == Role.ADMIN || user.getRole() == Role.DEMO)
                ? repository.findAll(pageable)
                : repository.findByUserId(user.getId(), pageable);
        return PageResponse.from(page.map(ActivityLogDto::from));
    }
}
