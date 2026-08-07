package es.docklite.docklitebackend.auth.controller;

import es.docklite.docklitebackend.auth.dto.CreateInvitationRequest;
import es.docklite.docklitebackend.auth.dto.InvitationDto;
import es.docklite.docklitebackend.auth.service.InvitationService;
import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/invitations")
@RequiredArgsConstructor
@Validated
public class AdminInvitationController {

    private final InvitationService invitationService;

    @GetMapping
    // DEMO gets the admin read-only view, but with tokens/URLs masked: an
    // active invitation link would let an anonymous visitor register for real.
    @PreAuthorize("hasAnyRole('ADMIN','DEMO')")
    public PageResponse<InvitationDto> list(
            Authentication auth,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 100, message = "size must be <= 100") int size) {
        var invitations = invitationService.listAll(PageRequest.of(page, size, Sort.by("id").descending()));
        boolean demo = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEMO".equals(a.getAuthority()));
        if (demo) {
            invitations = invitations.map(InvitationDto::masked);
        }
        return PageResponse.from(invitations);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationDto create(Authentication auth,
                                @Valid @RequestBody CreateInvitationRequest req) {
        return invitationService.create(req, (User) auth.getPrincipal());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        invitationService.cancel(id);
    }
}
